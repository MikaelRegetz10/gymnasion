package com.gymnasion.tcc.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificacaoResponseDTO(
        UUID id,
        String titulo,
        String mensagem,
        String tipo,
        Boolean lida,
        UUID referenciaId,
        OffsetDateTime dataCriacao
) {}
