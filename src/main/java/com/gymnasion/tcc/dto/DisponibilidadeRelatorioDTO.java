package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.TipoMetrica;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Datas e métricas com registros disponíveis para o relatório de performance (US-16).
 * Usa a mesma data de referência da geração, para que toda data listada produza relatório.
 */
public record DisponibilidadeRelatorioDTO(
        LocalDate dataInicio,
        LocalDate dataFim,
        long totalSessoes,
        List<ModalidadeDisponivelDTO> modalidades
) {

    public record ModalidadeDisponivelDTO(
            Long modalidadeId,
            String modalidade,
            LocalDate dataInicio,
            LocalDate dataFim,
            long totalSessoes,
            List<LocalDate> datas,
            List<LocalDate> datasRpe,
            List<MetricaDisponivelDTO> metricas
    ) {}

    public record MetricaDisponivelDTO(
            UUID metricaId,
            String titulo,
            TipoMetrica tipo,
            boolean descontinuada,
            String statusMetrica,
            long totalRegistros,
            List<LocalDate> datas
    ) {}
}
