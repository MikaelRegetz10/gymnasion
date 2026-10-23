package com.gymnasion.tcc.dto;

public record PreferenciaUsuarioResponseDTO(
        boolean showNotificationBadge,
        boolean notificacoesEmail,
        boolean notificacoesPush
) {}
