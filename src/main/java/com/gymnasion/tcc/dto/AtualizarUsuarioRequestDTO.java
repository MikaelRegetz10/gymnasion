package com.gymnasion.tcc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarUsuarioRequestDTO(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String nome,

        @NotBlank(message = "O celular é obrigatório.")
        @Size(max = 20, message = "O celular deve ter no máximo 20 caracteres.")
        String celular
) {}
