package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record AtualizarTurmaRequestDTO(
        @NotBlank(message = "O nome da turma é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        @Schema(description = "Nome da turma", example = "Turma Natação Manhã - Elite")
        String nome,

        @NotNull(message = "A modalidade é obrigatória.")
        @Schema(description = "ID da modalidade esportiva", example = "1")
        Long modalidadeId,

        @Schema(description = "Horário das aulas da turma (Opcional)", example = "2026-10-01T09:00:00Z")
        OffsetDateTime horario
) {}