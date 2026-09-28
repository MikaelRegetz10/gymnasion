package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record CriarTurmaRequestDTO(
        @NotBlank(message = "O nome da turma é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        @Schema(description = "Nome da turma", example = "Turma Natação Manhã - Avançado")
        String nome,

        @NotNull(message = "A modalidade é obrigatória.")
        @Schema(description = "ID da modalidade esportiva", example = "1")
        Long modalidadeId,

        @Schema(description = "Horário das aulas da turma (Opcional)", example = "2026-10-01T08:00:00Z")
        OffsetDateTime horario,

        @NotEmpty(message = "É necessário selecionar pelo menos 1 (um) aluno para criar uma turma.")
        @Schema(description = "Lista de IDs dos alunos vinculados à turma")
        Set<UUID> alunosIds
) {}