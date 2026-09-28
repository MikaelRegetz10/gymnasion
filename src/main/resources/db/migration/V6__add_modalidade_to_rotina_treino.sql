ALTER TABLE rotina_treino ADD COLUMN modalidade_id BIGINT;

UPDATE rotina_treino SET modalidade_id = 1 WHERE modalidade_id IS NULL;

ALTER TABLE rotina_treino ALTER COLUMN modalidade_id SET NOT NULL;

ALTER TABLE rotina_treino
    ADD CONSTRAINT fk_rotina_modalidade
        FOREIGN KEY (modalidade_id) REFERENCES modalidade(id) ON DELETE RESTRICT;