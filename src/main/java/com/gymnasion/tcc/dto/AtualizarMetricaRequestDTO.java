package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.TipoMetrica;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarMetricaRequestDTO(
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 100, message = "O título deve ter no máximo 100 caracteres.")
        String titulo,

        TipoMetrica tipo
) {}