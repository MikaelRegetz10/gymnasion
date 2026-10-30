package com.gymnasion.tcc.dto;

import java.time.LocalDate;

public record DiaRegistrosModalidadeDTO(
        Long modalidadeId,
        String modalidade,
        LocalDate data,
        Long totalSessoes,
        Long totalComRpe
) {}
