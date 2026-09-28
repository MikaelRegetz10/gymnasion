ALTER TABLE turma ADD COLUMN modalidade_id BIGINT;

UPDATE turma SET modalidade_id = 1 WHERE modalidade_id IS NULL;

ALTER TABLE turma ALTER COLUMN modalidade_id SET NOT NULL;

ALTER TABLE turma
    ADD CONSTRAINT fk_turma_modalidade
        FOREIGN KEY (modalidade_id) REFERENCES modalidade(id) ON DELETE RESTRICT;