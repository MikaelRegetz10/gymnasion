package com.gymnasion.tcc.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "preferencia_usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferenciaUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Builder.Default
    @Column(name = "show_notification_badge", nullable = false)
    private Boolean showNotificationBadge = true;

    @Builder.Default
    @Column(name = "notificacoes_email", nullable = false)
    private Boolean notificacoesEmail = false;

    @Builder.Default
    @Column(name = "notificacoes_push", nullable = false)
    private Boolean notificacoesPush = false;

    @Builder.Default
    @Column(name = "data_atualizacao", nullable = false)
    private OffsetDateTime dataAtualizacao = OffsetDateTime.now();
}
