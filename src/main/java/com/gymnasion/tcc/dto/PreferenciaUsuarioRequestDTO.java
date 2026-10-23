package com.gymnasion.tcc.dto;

public record PreferenciaUsuarioRequestDTO(
        Boolean showNotificationBadge,
        Boolean notificacoesEmail,
        Boolean notificacoesPush
) {}
