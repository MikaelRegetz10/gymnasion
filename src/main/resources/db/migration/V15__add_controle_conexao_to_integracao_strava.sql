-- V15__add_controle_conexao_to_integracao_strava.sql
-- Registra o escopo concedido, a data da conexao e se o aluno precisa reconectar o Strava

ALTER TABLE integracao_strava
    ADD COLUMN IF NOT EXISTS escopo VARCHAR(255),
    ADD COLUMN IF NOT EXISTS conectado_em TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reconexao_pendente BOOLEAN NOT NULL DEFAULT FALSE;
