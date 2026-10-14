package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AtualizarSessaoTreinoRequestDTO(
        @NotNull(message = "A semana é obrigatória.")
        @Min(value = 1, message = "O número da semana deve ser no mínimo 1.")
        @Schema(description = "Número da semana no planejamento", example = "2")
        Integer semana,

        @NotNull(message = "A data planejada é obrigatória.")
        @Schema(description = "Nova data planejada da sessão futura", example = "2026-10-25")
        LocalDate dataPlanejada,

        @Min(value = 1, message = "A duração deve ser de no mínimo 1 minuto.")
        @Schema(description = "Duração estimada em minutos", example = "50")
        Integer duracao,

        @Schema(description = "Orientações e atividades da sessão de treino", example = "Séries de resistência e técnicas de respiração.")
        String atividades
) {}
