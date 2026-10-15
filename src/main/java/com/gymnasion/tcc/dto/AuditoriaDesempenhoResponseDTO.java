package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.CampoAuditado;
import com.gymnasion.tcc.domain.enums.TipoOperacaoAuditoria;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditoriaDesempenhoResponseDTO(
        UUID id,
        TipoOperacaoAuditoria tipoOperacao,
        CampoAuditado campo,
        UUID metricaId,
        String tituloMetrica,
        String valorAnterior,
        String valorNovo,
        UUID usuarioId,
        String nomeUsuario,
        OffsetDateTime dataHora
) {}
