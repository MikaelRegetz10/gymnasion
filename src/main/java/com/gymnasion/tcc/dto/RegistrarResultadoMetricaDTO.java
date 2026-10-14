package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegistrarResultadoMetricaDTO(
        @NotNull(message = "O ID da métrica é obrigatório.")
        @Schema(description = "ID da métrica associada à rotina", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID metricaId,

        @NotBlank(message = "O valor da métrica é obrigatório.")
        @Schema(description = "Valor obtido na execução do treino", example = "01:15")
        String valor
) {}
