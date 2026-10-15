-- V12: US-23 - Correção de registros de desempenho com auditoria (inclusões e correções) e observações do desempenho

ALTER TABLE desempenho ADD COLUMN IF NOT EXISTS observacoes TEXT;

CREATE TABLE auditoria_desempenho (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    desempenho_id UUID NOT NULL,
    tipo_operacao VARCHAR(20) NOT NULL,
    campo VARCHAR(30) NOT NULL,
    metrica_id UUID,
    valor_anterior TEXT,
    valor_novo TEXT NOT NULL,
    usuario_id UUID NOT NULL,
    data_hora TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auditoria_desempenho FOREIGN KEY (desempenho_id)
        REFERENCES desempenho(id) ON DELETE CASCADE,
    CONSTRAINT fk_auditoria_metrica FOREIGN KEY (metrica_id)
        REFERENCES metrica(id) ON DELETE RESTRICT,
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE RESTRICT
);

CREATE INDEX idx_auditoria_desempenho_desempenho ON auditoria_desempenho(desempenho_id);
