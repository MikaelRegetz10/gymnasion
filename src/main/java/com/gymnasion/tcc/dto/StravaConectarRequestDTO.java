package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record StravaConectarRequestDTO(
        @Schema(description = "Authorization code retornado pelo Strava no callback OAuth 2.0 (opcional se fornecer tokens diretamente)", example = "9b642e1fb59132576b4a8e")
        String code,

        @Schema(description = "Access token direto do Strava (opcional, para conexão direta sem passar pelo redirect do OAuth)", example = "e7f8a9b0c1d2e3f4")
        String accessToken,

        @Schema(description = "Refresh token direto do Strava (opcional)", example = "a1b2c3d4e5f6a7b8")
        String refreshToken,

        @Schema(description = "Escopos autorizados pelo usuário", example = "read,activity:read_all")
        String scope
) {}
