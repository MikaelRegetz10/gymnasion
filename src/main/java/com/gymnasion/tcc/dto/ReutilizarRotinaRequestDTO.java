package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ReutilizarRotinaRequestDTO(
        @NotNull(message = "O ID do aluno é obrigatório.")
        @Schema(description = "ID do aluno para o qual a rotina será atribuída/reutilizada", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID alunoId,

        @NotNull(message = "A nova data de início é obrigatória.")
        @Schema(description = "Nova data de início da rotina de treino", example = "2026-11-01")
        LocalDate dataInicio,

        @Schema(description = "Título personalizado para a nova rotina (opcional; se omitido, mantém o título da rotina original)", example = "Treino de Hipertrofia A - Personalizado")
        String titulo,

        @Schema(description = "Objetivos personalizados da nova rotina (opcional; se omitido, mantém os objetivos da rotina original)")
        String objetivos
) {}
