-- V14__add_atleta_fields_to_integracao_strava.sql
-- Adiciona campos de identificacao do atleta do Strava

ALTER TABLE integracao_strava
    ADD COLUMN IF NOT EXISTS atleta_id BIGINT,
    ADD COLUMN IF NOT EXISTS nome_atleta VARCHAR(150);
