CREATE TABLE notificacao (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    UUID                     NOT NULL,
    titulo        VARCHAR(150)             NOT NULL,
    mensagem      TEXT                     NOT NULL,
    tipo          VARCHAR(50)              NOT NULL,
    lida          BOOLEAN                  NOT NULL DEFAULT FALSE,
    referencia_id UUID,
    data_criacao  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notificacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);

CREATE INDEX idx_notificacao_usuario ON notificacao(usuario_id);
CREATE INDEX idx_notificacao_lida    ON notificacao(usuario_id, lida);
CREATE INDEX idx_notificacao_tipo    ON notificacao(usuario_id, tipo);
