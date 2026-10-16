package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.dto.IntervaloRegistrosModalidadeDTO;
import com.gymnasion.tcc.dto.RelatorioPdfDTO;
import com.gymnasion.tcc.dto.RelatorioPerformanceDTO;
import com.gymnasion.tcc.dto.RelatorioPerformanceDTO.SecaoModalidadeRelatorioDTO;
import com.gymnasion.tcc.dto.RelatorioPerformanceDTO.SessaoRelatorioDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.exceptions.RelatorioSemDadosException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.DesempenhoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Emissão do relatório de performance do aluno em PDF (US-16).
 * Atende às regras de negócio RN-005, RN-006, RN-014, RN-017 e RN-018 e ao RNF-004.
 */
@Service
@RequiredArgsConstructor
public class RelatorioPerformanceService {

    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

    private final AlunoRepository alunoRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final DesempenhoRepository desempenhoRepository;
    private final RelatorioPdfGenerator relatorioPdfGenerator;

    @Transactional(readOnly = true)
    public RelatorioPdfDTO gerarRelatorioPeloPersonal(
            Usuario usuario,
            UUID alunoId,
            Long modalidadeId,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));

        // RN-006: o personal só emite relatórios dos alunos que ele cadastrou (inclusive inativos, RN-014)
        Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado para este Personal Trainer."));

        return gerarRelatorio(aluno, modalidadeId, dataInicio, dataFim);
    }

    @Transactional(readOnly = true)
    public RelatorioPdfDTO gerarRelatorioPeloAluno(
            Usuario usuario,
            Long modalidadeId,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        Aluno aluno = alunoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        // RN-014: Aluno inativo não pode acessar
        if (aluno.getStatus() == StatusConvite.INATIVO) {
            throw new BusinessException("Erro. Este aluno está inativo.");
        }

        return gerarRelatorio(aluno, modalidadeId, dataInicio, dataFim);
    }

    private RelatorioPdfDTO gerarRelatorio(
            Aluno aluno,
            Long modalidadeId,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        // US-16 C1: o usuário seleciona o período antes de confirmar a geração
        if (dataInicio == null || dataFim == null) {
            throw new BusinessException("Informe a data de início e a data de fim do período do relatório.");
        }
        if (dataInicio.isAfter(dataFim)) {
            throw new BusinessException("A data de início não pode ser posterior à data de fim.");
        }

        if (modalidadeId != null) {
            validarModalidadeDoAluno(aluno, modalidadeId);
        }

        List<Desempenho> desempenhos = desempenhoRepository
                .findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), dataInicio, dataFim)
                .stream()
                .filter(d -> modalidadeId == null || modalidadeId.equals(obterModalidade(d).getId()))
                .sorted(Comparator.comparing((Desempenho d) -> AgregadorEvolucaoDesempenho.obterDataReferencia(d.getSessaoTreino()))
                        .thenComparing(Desempenho::getDataRegistro))
                .toList();

        // US-16 C3: período sem dados, sugere um intervalo alternativo com registros
        if (desempenhos.isEmpty()) {
            throw criarExcecaoSemDados(aluno, modalidadeId);
        }

        Map<Long, List<Desempenho>> desempenhosPorModalidade = new TreeMap<>();
        Map<Long, String> nomesModalidades = new HashMap<>();
        for (Desempenho d : desempenhos) {
            Modalidade modalidade = obterModalidade(d);
            nomesModalidades.putIfAbsent(modalidade.getId(), modalidade.getNome());
            desempenhosPorModalidade.computeIfAbsent(modalidade.getId(), k -> new ArrayList<>()).add(d);
        }

        List<SecaoModalidadeRelatorioDTO> secoes = desempenhosPorModalidade.entrySet().stream()
                .map(entry -> montarSecao(nomesModalidades.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(SecaoModalidadeRelatorioDTO::modalidade))
                .toList();

        String nomeAluno = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : null;
        String nomePersonal = aluno.getPersonal() != null && aluno.getPersonal().getUsuario() != null
                ? aluno.getPersonal().getUsuario().getNome()
                : null;

        RelatorioPerformanceDTO relatorio = new RelatorioPerformanceDTO(
                nomeAluno,
                nomePersonal,
                dataInicio,
                dataFim,
                OffsetDateTime.now(FUSO_HORARIO),
                secoes
        );

        byte[] conteudo = relatorioPdfGenerator.gerar(relatorio);
        return new RelatorioPdfDTO(gerarNomeArquivo(nomeAluno, dataInicio, dataFim), conteudo);
    }

    private SecaoModalidadeRelatorioDTO montarSecao(String modalidade, List<Desempenho> desempenhos) {
        List<SessaoRelatorioDTO> sessoes = desempenhos.stream()
                .map(d -> {
                    SessaoTreino sessao = d.getSessaoTreino();
                    return new SessaoRelatorioDTO(
                            sessao.getId(),
                            sessao.getDataPlanejada(),
                            sessao.getDataReal(),
                            sessao.getDuracao(),
                            sessao.getStatus() == StatusSessaoTreino.CONCLUIDA ? "Concluída" : "Planejada",
                            // US-20 C2: RPE pulado aparece como "não informado"
                            d.getNivelCansaco() != null ? String.valueOf(d.getNivelCansaco()) : "não informado"
                    );
                })
                .toList();

        return new SecaoModalidadeRelatorioDTO(
                modalidade,
                AgregadorEvolucaoDesempenho.calcularResumoGeral(desempenhos.size(), desempenhos),
                sessoes,
                AgregadorEvolucaoDesempenho.agregarSeriesMetricas(desempenhos),
                AgregadorEvolucaoDesempenho.agregarEvolucaoRpe(desempenhos, desempenhos)
        );
    }

    private void validarModalidadeDoAluno(Aluno aluno, Long modalidadeId) {
        boolean praticada = aluno.getModalidades() != null && aluno.getModalidades().stream()
                .anyMatch(m -> modalidadeId.equals(m.getId()));
        if (praticada) {
            return;
        }

        boolean possuiHistorico = desempenhoRepository.findIntervalosRegistrosByAlunoId(aluno.getId()).stream()
                .anyMatch(i -> modalidadeId.equals(i.modalidadeId()));
        if (!possuiHistorico) {
            throw new NotFoundException("Modalidade não encontrada para este aluno.");
        }
    }

    private RelatorioSemDadosException criarExcecaoSemDados(Aluno aluno, Long modalidadeId) {
        List<IntervaloRegistrosModalidadeDTO> intervalos = desempenhoRepository.findIntervalosRegistrosByAlunoId(aluno.getId())
                .stream()
                .filter(i -> modalidadeId == null || modalidadeId.equals(i.modalidadeId()))
                .toList();

        LocalDate inicioSugerido = intervalos.stream()
                .map(IntervaloRegistrosModalidadeDTO::dataInicio)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDate fimSugerido = intervalos.stream()
                .map(IntervaloRegistrosModalidadeDTO::dataFim)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new RelatorioSemDadosException(inicioSugerido, fimSugerido);
    }

    private Modalidade obterModalidade(Desempenho desempenho) {
        return desempenho.getSessaoTreino().getRotinaTreino().getModalidade();
    }

    private String gerarNomeArquivo(String nomeAluno, LocalDate dataInicio, LocalDate dataFim) {
        String nome = nomeAluno != null ? nomeAluno : "aluno";
        String slug = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return "relatorio-performance-" + slug + "-" + dataInicio + "_" + dataFim + ".pdf";
    }
}
