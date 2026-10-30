package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.TipoMetrica;

import java.time.LocalDate;
import java.util.UUID;

public record DiaRegistrosMetricaDTO(
        Long modalidadeId,
        UUID metricaId,
        String titulo,
        TipoMetrica tipo,
        Boolean ativo,
        LocalDate data,
        Long totalRegistros
) {}
