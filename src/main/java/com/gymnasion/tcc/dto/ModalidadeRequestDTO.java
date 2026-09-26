package com.gymnasion.tcc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModalidadeRequestDTO(
        @NotBlank(message = "O nome da modalidade é obrigatório.")
        @Size(max = 50, message = "O nome deve ter no máximo 50 caracteres.")
        String nome,

        String descricao
) {}
