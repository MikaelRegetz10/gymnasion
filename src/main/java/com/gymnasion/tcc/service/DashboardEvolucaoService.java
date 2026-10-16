package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.DesempenhoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Serviço de agregação de dados para os dashboards e gráficos dinâmicos de evolução (US-15 / US-20 C3).
 * Atende às regras de negócio RN-005, RN-006, RN-014, RN-015, RN-017 e RN-018.
 */
@Service
@RequiredArgsConstructor
public class DashboardEvolucaoService {

    private final AlunoRepository alunoRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final DesempenhoRepository desempenhoRepository;

    @Transactional(readOnly = true)
    public DashboardEvolucaoResponseDTO obterDashboardPeloPersonal(
            Usuario usuario,
            UUID alunoId,
            Long modalidadeId,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));

        Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado para este Personal Trainer."));

        return gerarDashboard(aluno, modalidadeId, dataInicio, dataFim);
    }

    @Transactional(readOnly = true)
    public DashboardEvolucaoResponseDTO obterDashboardPeloAluno(
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

        return gerarDashboard(aluno, modalidadeId, dataInicio, dataFim);
    }

    private DashboardEvolucaoResponseDTO gerarDashboard(
            Aluno aluno,
            Long modalidadeId,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        // Validação de intervalo temporal (US-15 C2)
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new BusinessException("A data de início não pode ser posterior à data de fim.");
        }

        // Busca todos os desempenhos registrados para o aluno com suas sessões e métricas
        List<Desempenho> todosDesempenhos = desempenhoRepository.findByAlunoIdWithMetricas(aluno.getId());

        // Identifica todas as modalidades disponíveis (praticadas + histórico de treinos)
        Map<Long, Modalidade> mapaModalidades = new LinkedHashMap<>();
        if (aluno.getModalidades() != null) {
            for (Modalidade m : aluno.getModalidades()) {
                mapaModalidades.put(m.getId(), m);
            }
        }
        for (Desempenho d : todosDesempenhos) {
            if (d.getSessaoTreino() != null && d.getSessaoTreino().getRotinaTreino() != null) {
                Modalidade m = d.getSessaoTreino().getRotinaTreino().getModalidade();
                if (m != null) {
                    mapaModalidades.putIfAbsent(m.getId(), m);
                }
            }
        }

        List<ModalidadeResumoDashboardDTO> modalidadesDisponiveis = mapaModalidades.values().stream()
                .map(m -> new ModalidadeResumoDashboardDTO(m.getId(), m.getNome()))
                .toList();

        // Determina a modalidade ativa da consulta
        Modalidade modalidadeSelecionada = null;
        if (modalidadeId != null) {
            modalidadeSelecionada = mapaModalidades.get(modalidadeId);
            if (modalidadeSelecionada == null) {
                throw new NotFoundException("Modalidade não encontrada para este aluno.");
            }
        } else if (!mapaModalidades.isEmpty()) {
            modalidadeSelecionada = mapaModalidades.values().iterator().next();
        }

        Long finalModalidadeId = modalidadeSelecionada != null ? modalidadeSelecionada.getId() : null;

        // Histórico completo de desempenhos da modalidade (para checagem do limiar de 3 sessões - US-15 C1)
        List<Desempenho> desempenhosModalidade = todosDesempenhos.stream()
                .filter(d -> finalModalidadeId == null || (
                        d.getSessaoTreino() != null &&
                        d.getSessaoTreino().getRotinaTreino() != null &&
                        d.getSessaoTreino().getRotinaTreino().getModalidade() != null &&
                        finalModalidadeId.equals(d.getSessaoTreino().getRotinaTreino().getModalidade().getId())
                ))
                .sorted(Comparator.comparing((Desempenho d) -> AgregadorEvolucaoDesempenho.obterDataReferencia(d.getSessaoTreino()))
                        .thenComparing(Desempenho::getDataRegistro))
                .toList();

        int totalSessoesHistorico = desempenhosModalidade.size();
        boolean possuiDadosSuficientes = totalSessoesHistorico >= 3;
        String mensagem = possuiDadosSuficientes
                ? null
                : "São necessárias pelo menos 3 sessões registradas para exibir os gráficos de evolução.";

        // Filtro de período aplicado sobre o conjunto (US-15 C2)
        List<Desempenho> desempenhosPeriodo = desempenhosModalidade.stream()
                .filter(d -> {
                    LocalDate data = AgregadorEvolucaoDesempenho.obterDataReferencia(d.getSessaoTreino());
                    if (dataInicio != null && data.isBefore(dataInicio)) return false;
                    if (dataFim != null && data.isAfter(dataFim)) return false;
                    return true;
                })
                .toList();

        // Agregação das séries temporais das métricas
        List<SerieMetricaDashboardDTO> seriesMetricas = AgregadorEvolucaoDesempenho.agregarSeriesMetricas(desempenhosPeriodo);

        // Agregação da evolução do RPE (US-20 C3 / RN-018)
        EvolucaoRpeDashboardDTO evolucaoRpe = AgregadorEvolucaoDesempenho.agregarEvolucaoRpe(desempenhosModalidade, desempenhosPeriodo);

        // Resumo geral de sessões e adesão no período
        ResumoGeralDashboardDTO resumoGeral = AgregadorEvolucaoDesempenho.calcularResumoGeral(totalSessoesHistorico, desempenhosPeriodo);

        AlunoResumoDashboardDTO alunoDTO = new AlunoResumoDashboardDTO(
                aluno.getId(),
                aluno.getUsuario() != null ? aluno.getUsuario().getNome() : null,
                aluno.getUsuario() != null ? aluno.getUsuario().getEmail() : null
        );

        ModalidadeResumoDashboardDTO modalidadeDTO = modalidadeSelecionada != null
                ? new ModalidadeResumoDashboardDTO(modalidadeSelecionada.getId(), modalidadeSelecionada.getNome())
                : null;

        PeriodoDashboardDTO periodoDTO = new PeriodoDashboardDTO(dataInicio, dataFim);

        return new DashboardEvolucaoResponseDTO(
                alunoDTO,
                modalidadeDTO,
                modalidadesDisponiveis,
                periodoDTO,
                resumoGeral,
                possuiDadosSuficientes,
                mensagem,
                seriesMetricas,
                evolucaoRpe
        );
    }
}
