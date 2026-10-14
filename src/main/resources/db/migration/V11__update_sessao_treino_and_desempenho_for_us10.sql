-- V11: Atualização das tabelas sessao_treino e desempenho para suportar US-10 (gerenciamento de rotinas, planejamento por semanas, datas planejada/real e estados)

ALTER TABLE sessao_treino RENAME COLUMN data_execucao TO data_planejada;
ALTER TABLE sessao_treino ADD COLUMN IF NOT EXISTS data_real DATE;
ALTER TABLE sessao_treino ADD COLUMN IF NOT EXISTS semana INT NOT NULL DEFAULT 1;
ALTER TABLE sessao_treino ADD COLUMN IF NOT EXISTS atividades TEXT;
ALTER TABLE sessao_treino ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PLANEJADA';
ALTER TABLE sessao_treino ADD COLUMN IF NOT EXISTS concluida_aluno BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sessao_treino ADD COLUMN IF NOT EXISTS validada_personal BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE desempenho ADD COLUMN IF NOT EXISTS aluno_id UUID;
ALTER TABLE desempenho ADD CONSTRAINT fk_desempenho_aluno FOREIGN KEY (aluno_id) REFERENCES aluno(id) ON DELETE SET NULL;
ALTER TABLE desempenho ADD COLUMN IF NOT EXISTS registrado_por VARCHAR(50);
