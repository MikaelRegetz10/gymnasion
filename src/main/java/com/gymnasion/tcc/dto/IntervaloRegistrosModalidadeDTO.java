package com.gymnasion.tcc.dto;

import java.time.LocalDate;

public record IntervaloRegistrosModalidadeDTO(
        Long modalidadeId,
        LocalDate dataInicio,
        LocalDate dataFim
) {}
