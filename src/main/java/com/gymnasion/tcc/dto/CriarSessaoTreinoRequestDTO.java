package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CriarSessaoTreinoRequestDTO(
        @NotNull(message = "A semana é obrigatória.")
        @Min(value = 1, message = "O número da semana deve ser no mínimo 1.")
        @Schema(description = "Número da semana dentro do planejamento da rotina", example = "1")
        Integer semana,

        @NotNull(message = "A data planejada é obrigatória.")
        @Schema(description = "Data planejada para a realização da sessão", example = "2026-10-20")
        LocalDate dataPlanejada,

        @Min(value = 1, message = "A duração deve ser de no mínimo 1 minuto.")
        @Schema(description = "Duração estimada em minutos", example = "60")
        Integer duracao,

        @Schema(description = "Orientações e atividades da sessão de treino", example = "Aquecimento 10 min, 4x100m livre, arrefecimento.")
        String atividades
) {}
