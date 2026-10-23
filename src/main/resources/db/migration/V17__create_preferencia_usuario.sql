CREATE TABLE preferencia_usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL UNIQUE,
    show_notification_badge BOOLEAN NOT NULL DEFAULT TRUE,
    notificacoes_email BOOLEAN NOT NULL DEFAULT FALSE,
    notificacoes_push BOOLEAN NOT NULL DEFAULT FALSE,
    data_atualizacao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_preferencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);
