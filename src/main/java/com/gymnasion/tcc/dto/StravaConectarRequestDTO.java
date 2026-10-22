package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record StravaConectarRequestDTO(
        @NotBlank(message = "Informe o código de autorização retornado pelo Strava.")
        @Schema(description = "Authorization code retornado pelo Strava no callback OAuth 2.0", example = "9b642e1fb59132576b4a8e")
        String code,

        @Schema(description = "Escopos autorizados pelo usuário, como retornados no callback", example = "read,activity:read_all")
        String scope,

        @Schema(description = "Valor de 'state' retornado no callback, gerado pelo endpoint auth-url")
        String state
) {}
