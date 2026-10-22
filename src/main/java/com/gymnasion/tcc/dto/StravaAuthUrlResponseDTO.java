package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record StravaAuthUrlResponseDTO(
        @Schema(description = "URL de autorização OAuth 2.0 do Strava para redirecionamento do usuário")
        String authUrl,

        @Schema(description = "Indica se o backend está operando em modo sandbox simulado")
        boolean mock
) {}
