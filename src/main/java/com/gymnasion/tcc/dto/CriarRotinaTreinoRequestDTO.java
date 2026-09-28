package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.Set;
import java.util.UUID;

public record CriarRotinaTreinoRequestDTO(
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        @Schema(description = "Título da rotina de treino", example = "Treino de Hipertrofia A - Membros Superiores")
        String titulo,

        @NotNull(message = "A modalidade é obrigatória.")
        @Schema(description = "ID da modalidade esportiva", example = "1")
        Long modalidadeId,

        @NotNull(message = "A frequência semanal é obrigatória.")
        @Min(value = 1, message = "A frequência semanal deve estar entre 1 e 7 dias.")
        @Max(value = 7, message = "A frequência semanal deve estar entre 1 e 7 dias.")
        @Schema(description = "Frequência semanal de treinos (entre 1 e 7 dias)", example = "4")
        Integer frequenciaSemanal,

        @Schema(description = "Objetivos do treino", example = "Ganho de massa magra e resistência muscular.")
        String objetivos,

        @NotEmpty(message = "É necessário selecionar pelo menos 1 (um) aluno.")
        @Schema(description = "Lista de IDs dos alunos vinculados à rotina")
        Set<UUID> alunosIds,

        @NotEmpty(message = "É necessário selecionar pelo menos 1 (uma) métrica personalizada.")
        @Schema(description = "Lista de IDs das métricas personalizadas da modalidade vinculadas a esta rotina")
        Set<UUID> metricasIds
) {}