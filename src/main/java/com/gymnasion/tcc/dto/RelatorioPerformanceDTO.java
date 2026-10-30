package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.ConteudoRelatorio;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.EvolucaoRpeDashboardDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.ResumoGeralDashboardDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.SerieMetricaDashboardDTO;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dados consolidados do relatório de performance em PDF (US-16).
 */
public record RelatorioPerformanceDTO(
        String nomeAluno,
        String nomePersonal,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer totalDatasSelecionadas,
        OffsetDateTime dataGeracao,
        Set<ConteudoRelatorio> conteudos,
        List<SecaoModalidadeRelatorioDTO> secoes
) {

    public record SecaoModalidadeRelatorioDTO(
            String modalidade,
            ResumoGeralDashboardDTO resumoGeral,
            List<SessaoRelatorioDTO> sessoes,
            List<SerieMetricaDashboardDTO> metricas,
            EvolucaoRpeDashboardDTO evolucaoRpe
    ) {}

    public record SessaoRelatorioDTO(
            UUID sessaoId,
            LocalDate dataPlanejada,
            LocalDate dataReal,
            Integer duracao,
            String status,
            String rpe
    ) {}
}
