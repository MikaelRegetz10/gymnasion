package com.gymnasion.tcc.dto;

import jakarta.validation.constraints.NotBlank;

public record AlterarSenhaRequestDTO(
        @NotBlank(message = "A senha atual é obrigatória.")
        String senhaAtual,

        @NotBlank(message = "A nova senha é obrigatória.")
        String novaSenha,

        @NotBlank(message = "A confirmação da nova senha é obrigatória.")
        String confirmacaoNovaSenha
) {}
