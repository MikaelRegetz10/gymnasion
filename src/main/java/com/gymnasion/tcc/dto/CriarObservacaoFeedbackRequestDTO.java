package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarObservacaoFeedbackRequestDTO(
        @Schema(description = "Conteúdo textual da observação ou feedback", example = "Excelente evolução na execução dos treinos desta semana.")
        @NotBlank(message = "O conteúdo é obrigatório.")
        @Size(max = 2000, message = "O conteúdo deve ter no máximo 2000 caracteres.")
        String conteudo
) {
}
