package com.gymnasion.tcc.dto;

import jakarta.validation.constraints.NotNull;

public record GerarConviteRequestDTO(
        @NotNull(message = "A modalidade é obrigatória.")
        Long modalidadeId
) {}
