package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.TipoMetrica;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DashboardEvolucaoResponseDTO(
        AlunoResumoDashboardDTO aluno,
        ModalidadeResumoDashboardDTO modalidade,
        List<ModalidadeResumoDashboardDTO> modalidadesDisponiveis,
        PeriodoDashboardDTO periodo,
        ResumoGeralDashboardDTO resumoGeral,
        boolean possuiDadosSuficientes,
        String mensagem,
        List<SerieMetricaDashboardDTO> metricas,
        EvolucaoRpeDashboardDTO evolucaoRpe
) {

    public record AlunoResumoDashboardDTO(
            UUID id,
            String nome,
            String email
    ) {}

    public record ModalidadeResumoDashboardDTO(
            Long id,
            String nome
    ) {}

    public record PeriodoDashboardDTO(
            LocalDate dataInicio,
            LocalDate dataFim
    ) {}

    public record ResumoGeralDashboardDTO(
            int totalSessoesHistorico,
            int totalSessoesPeriodo,
            int totalSessoesConcluidasPeriodo,
            Double duracaoMediaMinutos,
            Double taxaAdesaoPercentual
    ) {}

    public record SerieMetricaDashboardDTO(
            UUID metricaId,
            String titulo,
            TipoMetrica tipo,
            boolean obrigatoria,
            ResponsavelPreenchimento quemPreenche,
            boolean descontinuada,
            String statusMetrica,
            TendenciaMetricaDTO tendencia,
            List<PontoMetricaDashboardDTO> pontos
    ) {}

    public record TendenciaMetricaDTO(
            Double valorInicial,
            Double valorAtual,
            Double variacaoAbsoluta,
            Double percentualVariacao,
            String direcao,
            String indicadorVisual,
            String resumo
    ) {}

    public record PontoMetricaDashboardDTO(
            UUID sessaoId,
            LocalDate data,
            String valor,
            Double valorNumerico,
            String formatado
    ) {}

    public record EvolucaoRpeDashboardDTO(
            int totalSessoesComRpe,
            boolean possuiDadosSuficientes,
            String mensagem,
            Double mediaRpe,
            Integer ultimoRpe,
            String direcaoTendencia,
            List<PontoRpeDashboardDTO> pontos
    ) {}

    public record PontoRpeDashboardDTO(
            UUID sessaoId,
            LocalDate data,
            Integer nivelCansaco,
            String descricao
    ) {}
}
