package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record StravaStatusResponseDTO(
        @Schema(description = "Indica se o aluno possui integração ativa com o Strava", example = "true")
        boolean conectado,

        @Schema(description = "Indica se o token de acesso do Strava expirou ou foi revogado (US-14 C3)", example = "false")
        boolean expirado,

        @Schema(description = "ID do atleta no Strava", example = "12345678")
        Long atletaId,

        @Schema(description = "Nome do atleta cadastrado no Strava", example = "Mikael Regetz")
        String nomeAtleta,

        @Schema(description = "Indica se a integração está rodando em modo sandbox simulado", example = "false")
        boolean mock
) {}
