package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.ConteudoRelatorio;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import com.gymnasion.tcc.dto.DiaRegistrosMetricaDTO;
import com.gymnasion.tcc.dto.DiaRegistrosModalidadeDTO;
import com.gymnasion.tcc.dto.DisponibilidadeRelatorioDTO;
import com.gymnasion.tcc.dto.DisponibilidadeRelatorioDTO.MetricaDisponivelDTO;
import com.gymnasion.tcc.dto.DisponibilidadeRelatorioDTO.ModalidadeDisponivelDTO;
import com.gymnasion.tcc.dto.FiltroRelatorioDTO;
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
import java.util.stream.Collectors;

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
    public RelatorioPdfDTO gerarRelatorioPeloPersonal(Usuario usuario, UUID alunoId, FiltroRelatorioDTO filtro) {
        return gerarRelatorio(obterAlunoDoPersonal(usuario, alunoId), filtro);
    }

    @Transactional(readOnly = true)
    public RelatorioPdfDTO gerarRelatorioPeloAluno(Usuario usuario, FiltroRelatorioDTO filtro) {
        return gerarRelatorio(obterAlunoAtivo(usuario), filtro);
    }

    @Transactional(readOnly = true)
    public DisponibilidadeRelatorioDTO consultarDisponibilidadePeloPersonal(Usuario usuario, UUID alunoId) {
        return consultarDisponibilidade(obterAlunoDoPersonal(usuario, alunoId));
    }

    @Transactional(readOnly = true)
    public DisponibilidadeRelatorioDTO consultarDisponibilidadePeloAluno(Usuario usuario) {
        return consultarDisponibilidade(obterAlunoAtivo(usuario));
    }

    private Aluno obterAlunoDoPersonal(Usuario usuario, UUID alunoId) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));

        // RN-006: o personal só emite relatórios dos alunos que ele cadastrou (inclusive inativos, RN-014)
        return alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado para este Personal Trainer."));
    }

    private Aluno obterAlunoAtivo(Usuario usuario) {
        Aluno aluno = alunoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        // RN-014: Aluno inativo não pode acessar
        if (aluno.getStatus() == StatusConvite.INATIVO) {
            throw new BusinessException("Erro. Este aluno está inativo.");
        }
        return aluno;
    }

    private RelatorioPdfDTO gerarRelatorio(Aluno aluno, FiltroRelatorioDTO filtro) {
        Long modalidadeId = filtro.modalidadeId();
        LocalDate dataInicio = filtro.dataInicio();
        LocalDate dataFim = filtro.dataFim();

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
        validarMetricasDoAluno(aluno, filtro);

        List<Desempenho> desempenhos = desempenhoRepository
                .findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), dataInicio, dataFim)
                .stream()
                .filter(d -> modalidadeId == null || modalidadeId.equals(obterModalidade(d).getId()))
                .filter(d -> filtro.incluiData(AgregadorEvolucaoDesempenho.obterDataReferencia(d.getSessaoTreino())))
                .sorted(Comparator.comparing((Desempenho d) -> AgregadorEvolucaoDesempenho.obterDataReferencia(d.getSessaoTreino()))
                        .thenComparing(Desempenho::getDataRegistro))
                .toList();

        Map<Long, List<Desempenho>> desempenhosPorModalidade = new TreeMap<>();
        Map<Long, String> nomesModalidades = new HashMap<>();
        for (Desempenho d : desempenhos) {
            Modalidade modalidade = obterModalidade(d);
            nomesModalidades.putIfAbsent(modalidade.getId(), modalidade.getNome());
            desempenhosPorModalidade.computeIfAbsent(modalidade.getId(), k -> new ArrayList<>()).add(d);
        }

        List<SecaoModalidadeRelatorioDTO> secoes = desempenhosPorModalidade.entrySet().stream()
                .map(entry -> montarSecao(nomesModalidades.get(entry.getKey()), entry.getValue(), filtro))
                .filter(secao -> possuiConteudo(secao, filtro))
                .sorted(Comparator.comparing(SecaoModalidadeRelatorioDTO::modalidade))
                .toList();

        // US-16 C3: nada a exibir no período, sugere um intervalo alternativo com registros
        if (secoes.isEmpty()) {
            throw criarExcecaoSemDados(aluno, filtro);
        }

        String nomeAluno = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : null;
        String nomePersonal = aluno.getPersonal() != null && aluno.getPersonal().getUsuario() != null
                ? aluno.getPersonal().getUsuario().getNome()
                : null;

        RelatorioPerformanceDTO relatorio = new RelatorioPerformanceDTO(
                nomeAluno,
                nomePersonal,
                dataInicio,
                dataFim,
                filtro.selecionouDatas() ? filtro.datas().size() : null,
                OffsetDateTime.now(FUSO_HORARIO),
                filtro.conteudosSelecionados(),
                secoes
        );

        byte[] conteudo = relatorioPdfGenerator.gerar(relatorio);
        return new RelatorioPdfDTO(gerarNomeArquivo(nomeAluno, dataInicio, dataFim), conteudo);
    }

    private SecaoModalidadeRelatorioDTO montarSecao(String modalidade, List<Desempenho> desempenhos, FiltroRelatorioDTO filtro) {
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
                AgregadorEvolucaoDesempenho.agregarSeriesMetricas(desempenhos).stream()
                        .filter(serie -> filtro.incluiMetrica(serie.metricaId()))
                        .toList(),
                AgregadorEvolucaoDesempenho.agregarEvolucaoRpe(desempenhos, desempenhos)
        );
    }

    private boolean possuiConteudo(SecaoModalidadeRelatorioDTO secao, FiltroRelatorioDTO filtro) {
        // Com métricas escolhidas, só entram as modalidades que têm alguma delas no período
        if (filtro.selecionouMetricas() && secao.metricas().isEmpty()) {
            return false;
        }

        Set<ConteudoRelatorio> conteudos = filtro.conteudosSelecionados();
        return conteudos.contains(ConteudoRelatorio.RESUMO)
                || conteudos.contains(ConteudoRelatorio.SESSOES)
                || (conteudos.contains(ConteudoRelatorio.VALORES) && !secao.metricas().isEmpty())
                || (conteudos.contains(ConteudoRelatorio.GRAFICOS)
                        && secao.metricas().stream().anyMatch(serie -> serie.tipo() != TipoMetrica.TEXTO))
                || (conteudos.contains(ConteudoRelatorio.RPE) && secao.evolucaoRpe().mediaRpe() != null);
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

    // RN-005/006: só aceita métricas com registros do próprio aluno
    private void validarMetricasDoAluno(Aluno aluno, FiltroRelatorioDTO filtro) {
        if (!filtro.selecionouMetricas()) {
            return;
        }

        Set<UUID> metricasDoAluno = desempenhoRepository.findDiasRegistrosMetricasByAlunoId(aluno.getId()).stream()
                .map(DiaRegistrosMetricaDTO::metricaId)
                .collect(Collectors.toSet());
        if (!metricasDoAluno.containsAll(filtro.metricaIds())) {
            throw new NotFoundException("Métrica não encontrada para este aluno.");
        }
    }

    private RelatorioSemDadosException criarExcecaoSemDados(Aluno aluno, FiltroRelatorioDTO filtro) {
        // O intervalo sugerido respeita as escolhas do usuário, para não levar a outra tentativa sem dados
        List<LocalDate> datas = consultarDisponibilidade(aluno).modalidades().stream()
                .filter(m -> filtro.modalidadeId() == null || filtro.modalidadeId().equals(m.modalidadeId()))
                .flatMap(m -> datasComConteudo(m, filtro).stream())
                .toList();

        if (datas.isEmpty()) {
            return new RelatorioSemDadosException(null, null);
        }
        return new RelatorioSemDadosException(Collections.min(datas), Collections.max(datas));
    }

    private Set<LocalDate> datasComConteudo(ModalidadeDisponivelDTO modalidade, FiltroRelatorioDTO filtro) {
        List<MetricaDisponivelDTO> metricas = modalidade.metricas().stream()
                .filter(m -> filtro.incluiMetrica(m.metricaId()))
                .toList();
        if (filtro.selecionouMetricas() && metricas.isEmpty()) {
            return Set.of();
        }

        Set<ConteudoRelatorio> conteudos = filtro.conteudosSelecionados();
        Set<LocalDate> datas = new HashSet<>();
        if (conteudos.contains(ConteudoRelatorio.RESUMO) || conteudos.contains(ConteudoRelatorio.SESSOES)) {
            datas.addAll(modalidade.datas());
        }
        if (conteudos.contains(ConteudoRelatorio.RPE)) {
            datas.addAll(modalidade.datasRpe());
        }
        for (MetricaDisponivelDTO metrica : metricas) {
            if (conteudos.contains(ConteudoRelatorio.VALORES)
                    || (conteudos.contains(ConteudoRelatorio.GRAFICOS) && metrica.tipo() != TipoMetrica.TEXTO)) {
                datas.addAll(metrica.datas());
            }
        }
        return datas;
    }

    private DisponibilidadeRelatorioDTO consultarDisponibilidade(Aluno aluno) {
        Map<Long, List<DiaRegistrosModalidadeDTO>> diasPorModalidade = desempenhoRepository.findDiasRegistrosByAlunoId(aluno.getId())
                .stream()
                .collect(Collectors.groupingBy(DiaRegistrosModalidadeDTO::modalidadeId));
        Map<Long, List<DiaRegistrosMetricaDTO>> metricasPorModalidade = desempenhoRepository.findDiasRegistrosMetricasByAlunoId(aluno.getId())
                .stream()
                .collect(Collectors.groupingBy(DiaRegistrosMetricaDTO::modalidadeId));

        List<ModalidadeDisponivelDTO> modalidades = diasPorModalidade.entrySet().stream()
                .map(entry -> montarModalidadeDisponivel(entry.getValue(), metricasPorModalidade.getOrDefault(entry.getKey(), List.of())))
                .sorted(Comparator.comparing(ModalidadeDisponivelDTO::modalidade))
                .toList();

        return new DisponibilidadeRelatorioDTO(
                modalidades.stream().map(ModalidadeDisponivelDTO::dataInicio).min(Comparator.naturalOrder()).orElse(null),
                modalidades.stream().map(ModalidadeDisponivelDTO::dataFim).max(Comparator.naturalOrder()).orElse(null),
                modalidades.stream().mapToLong(ModalidadeDisponivelDTO::totalSessoes).sum(),
                modalidades
        );
    }

    private ModalidadeDisponivelDTO montarModalidadeDisponivel(List<DiaRegistrosModalidadeDTO> dias, List<DiaRegistrosMetricaDTO> diasMetricas) {
        List<LocalDate> datas = dias.stream().map(DiaRegistrosModalidadeDTO::data).sorted().toList();
        List<LocalDate> datasRpe = dias.stream()
                .filter(dia -> dia.totalComRpe() > 0)
                .map(DiaRegistrosModalidadeDTO::data)
                .sorted()
                .toList();

        List<MetricaDisponivelDTO> metricas = diasMetricas.stream()
                .collect(Collectors.groupingBy(DiaRegistrosMetricaDTO::metricaId))
                .values().stream()
                .map(this::montarMetricaDisponivel)
                .sorted(Comparator.comparing(MetricaDisponivelDTO::titulo))
                .toList();

        return new ModalidadeDisponivelDTO(
                dias.get(0).modalidadeId(),
                dias.get(0).modalidade(),
                datas.get(0),
                datas.get(datas.size() - 1),
                dias.stream().mapToLong(DiaRegistrosModalidadeDTO::totalSessoes).sum(),
                datas,
                datasRpe,
                metricas
        );
    }

    private MetricaDisponivelDTO montarMetricaDisponivel(List<DiaRegistrosMetricaDTO> dias) {
        DiaRegistrosMetricaDTO metrica = dias.get(0);
        boolean descontinuada = !Boolean.TRUE.equals(metrica.ativo());
        return new MetricaDisponivelDTO(
                metrica.metricaId(),
                metrica.titulo(),
                metrica.tipo(),
                descontinuada,
                // RN-017: mantida no histórico como "métrica descontinuada"
                descontinuada ? "métrica descontinuada" : "ATIVA",
                dias.stream().mapToLong(DiaRegistrosMetricaDTO::totalRegistros).sum(),
                dias.stream().map(DiaRegistrosMetricaDTO::data).sorted().toList()
        );
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
