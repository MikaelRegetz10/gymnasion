package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

public record VincularAlunosRotinaRequestDTO(
        @NotEmpty(message = "É necessário selecionar pelo menos 1 (um) aluno.")
        @Schema(description = "Lista de IDs dos alunos a vincular à rotina de treino", example = "[\"e4b1626f-9721-4d37-8cf1-fcbc79f15be3\"]")
        Set<UUID> alunosIds
) {}
