package com.gymnasion.tcc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record GerarConviteRequestDTO(
        @NotNull(message = "A modalidade é obrigatória.")
        Long modalidadeId,

        @Email(message = "O email do aluno deve ser válido.")
        String emailAluno
) {}
