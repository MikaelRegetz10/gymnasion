-- V13: US-26 - Registrar observações e feedback no perfil do aluno (imutáveis)

CREATE TABLE observacao_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aluno_id UUID NOT NULL,
    autor_id UUID NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    conteudo TEXT NOT NULL,
    data_criacao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_observacao_feedback_aluno FOREIGN KEY (aluno_id)
        REFERENCES aluno(id) ON DELETE CASCADE,
    CONSTRAINT fk_observacao_feedback_autor FOREIGN KEY (autor_id)
        REFERENCES usuario(id) ON DELETE RESTRICT
);

CREATE INDEX idx_observacao_feedback_aluno ON observacao_feedback(aluno_id);
CREATE INDEX idx_observacao_feedback_data ON observacao_feedback(aluno_id, data_criacao DESC);
