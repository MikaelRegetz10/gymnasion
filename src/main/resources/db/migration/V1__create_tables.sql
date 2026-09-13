CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    cpf VARCHAR(11) NOT NULL UNIQUE,
    data_criacao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    celular VARCHAR(13) NOT NULL,
    role VARCHAR(20) NOT NULL
);

CREATE TABLE modalidade (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(50) NOT NULL,
    descricao TEXT
);

CREATE TABLE personal_trainer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL UNIQUE,
    modalidade_id BIGINT, 
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_personal_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_personal_modalidade FOREIGN KEY (modalidade_id)
    REFERENCES modalidade(id) ON DELETE SET NULL
);

CREATE TABLE aluno (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL UNIQUE,
    personal_id UUID NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_aluno_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_aluno_personal FOREIGN KEY (personal_id)
    REFERENCES personal_trainer(id) ON DELETE RESTRICT
);

CREATE TABLE define_usuario_personal_trainer_aluno (
    usuario_id UUID NOT NULL,
    personal_trainer_id UUID NOT NULL,
    aluno_id UUID NOT NULL,
    PRIMARY KEY (usuario_id, personal_trainer_id, aluno_id),
    CONSTRAINT fk_define_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_define_personal FOREIGN KEY (personal_trainer_id)
    REFERENCES personal_trainer(id) ON DELETE CASCADE,
    CONSTRAINT fk_define_aluno FOREIGN KEY (aluno_id)
    REFERENCES aluno(id) ON DELETE CASCADE
);

CREATE TABLE termo_aceite (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    versao VARCHAR(20) NOT NULL,
    data_aceite TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_termo_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id) ON DELETE CASCADE
);

CREATE TABLE integracao_strava (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aluno_id UUID NOT NULL UNIQUE,
    access_token VARCHAR(255) NOT NULL,
    refresh_token VARCHAR(255) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_strava_aluno FOREIGN KEY (aluno_id)
    REFERENCES aluno(id) ON DELETE CASCADE
);

CREATE TABLE turma (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    personal_id UUID NOT NULL,
    horario TIMESTAMPTZ,
    CONSTRAINT fk_turma_personal FOREIGN KEY (personal_id)
    REFERENCES personal_trainer(id) ON DELETE CASCADE
);

CREATE TABLE praticada (
    aluno_id UUID NOT NULL,
    modalidade_id BIGINT NOT NULL, 
    PRIMARY KEY (aluno_id, modalidade_id),
    CONSTRAINT fk_praticada_aluno FOREIGN KEY (aluno_id)
    REFERENCES aluno(id) ON DELETE CASCADE,
    CONSTRAINT fk_praticada_modalidade FOREIGN KEY (modalidade_id)
    REFERENCES modalidade(id) ON DELETE CASCADE
);

CREATE TABLE contem (
    aluno_id UUID NOT NULL,
    turma_id UUID NOT NULL,
    PRIMARY KEY (aluno_id, turma_id),
    CONSTRAINT fk_contem_aluno FOREIGN KEY (aluno_id)
    REFERENCES aluno(id) ON DELETE CASCADE,
    CONSTRAINT fk_contem_turma FOREIGN KEY (turma_id)
    REFERENCES turma(id) ON DELETE CASCADE
);

CREATE TABLE rotina_treino (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo VARCHAR(100) NOT NULL,
    objetivo VARCHAR(255),
    frequencia_semanal INT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    personal_trainer_id UUID NOT NULL,
    CONSTRAINT fk_rotina_personal FOREIGN KEY (personal_trainer_id)
    REFERENCES personal_trainer(id) ON DELETE CASCADE
);

CREATE TABLE segue (
    aluno_id UUID NOT NULL,
    rotina_treino_id UUID NOT NULL,
    PRIMARY KEY (aluno_id, rotina_treino_id),
    CONSTRAINT fk_segue_aluno FOREIGN KEY (aluno_id)
    REFERENCES aluno(id) ON DELETE CASCADE,
    CONSTRAINT fk_segue_rotina FOREIGN KEY (rotina_treino_id)
    REFERENCES rotina_treino(id) ON DELETE CASCADE
);

CREATE TABLE sessao_treino (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rotina_treino_id UUID NOT NULL,
    data_execucao DATE NOT NULL,
    duracao INT,
    CONSTRAINT fk_sessao_rotina FOREIGN KEY (rotina_treino_id)
    REFERENCES rotina_treino(id) ON DELETE CASCADE
);

CREATE TABLE desempenho (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sessao_treino_id UUID NOT NULL UNIQUE,
    data_registro TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    nivel_cansaco INT CHECK (nivel_cansaco BETWEEN 1 AND 10),
    CONSTRAINT fk_desempenho_sessao FOREIGN KEY (sessao_treino_id)
    REFERENCES sessao_treino(id) ON DELETE CASCADE
);

CREATE TABLE metrica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo VARCHAR(100) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    modalidade_id BIGINT NOT NULL,
    personal_trainer_id UUID NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_metrica_modalidade FOREIGN KEY (modalidade_id)
    REFERENCES modalidade(id) ON DELETE RESTRICT,
    CONSTRAINT fk_metrica_personal FOREIGN KEY (personal_trainer_id)
    REFERENCES personal_trainer(id) ON DELETE CASCADE,
    CONSTRAINT uq_metrica_titulo_personal_modalidade UNIQUE (titulo, personal_trainer_id, modalidade_id)
);

CREATE TABLE registro_metrica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    desempenho_id UUID NOT NULL,
    metrica_id UUID NOT NULL,
    valor VARCHAR(255) NOT NULL,
    CONSTRAINT fk_registro_desempenho FOREIGN KEY (desempenho_id)
    REFERENCES desempenho(id) ON DELETE CASCADE,
    CONSTRAINT fk_registro_metrica FOREIGN KEY (metrica_id)
    REFERENCES metrica(id) ON DELETE RESTRICT
);

CREATE TABLE relatorio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    personal_trainer_id UUID NOT NULL,
    CONSTRAINT fk_relatorio_personal FOREIGN KEY (personal_trainer_id)
    REFERENCES personal_trainer(id) ON DELETE CASCADE
);

CREATE TABLE consolida (
    relatorio_id UUID NOT NULL,
    desempenho_id UUID NOT NULL,
    PRIMARY KEY (relatorio_id, desempenho_id),
    CONSTRAINT fk_consolida_relatorio FOREIGN KEY (relatorio_id)
    REFERENCES relatorio(id) ON DELETE CASCADE,
    CONSTRAINT fk_consolida_desempenho FOREIGN KEY (desempenho_id)
    REFERENCES desempenho(id) ON DELETE CASCADE
);

CREATE TABLE personal_convites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(255) UNIQUE NOT NULL,
    personal_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    maximo_usuarios INT DEFAULT 30,
    quantidades_usuarios INT DEFAULT 0,
    data_expiracao TIMESTAMP NOT NULL,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_personal FOREIGN KEY (personal_id)
    REFERENCES personal_trainer(id) ON DELETE CASCADE
);

CREATE INDEX idx_usuario_email ON usuario(email);
CREATE INDEX idx_aluno_personal ON aluno(personal_id);
CREATE INDEX idx_turma_personal ON turma(personal_id);
CREATE INDEX idx_rotina_personal ON rotina_treino(personal_trainer_id);
CREATE INDEX idx_sessao_rotina ON sessao_treino(rotina_treino_id);
CREATE INDEX idx_registro_desempenho ON registro_metrica(desempenho_id);
CREATE INDEX idx_registro_metrica ON registro_metrica(metrica_id);