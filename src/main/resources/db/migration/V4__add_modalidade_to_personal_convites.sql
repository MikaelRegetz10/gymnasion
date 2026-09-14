ALTER TABLE personal_convites
ADD COLUMN modalidade_id BIGINT;

UPDATE personal_convites
SET modalidade_id = 1
WHERE modalidade_id IS NULL;

ALTER TABLE personal_convites
ALTER COLUMN modalidade_id SET NOT NULL;

ALTER TABLE personal_convites
ADD CONSTRAINT fk_convites_modalidade
FOREIGN KEY (modalidade_id) REFERENCES modalidade(id) ON DELETE RESTRICT;