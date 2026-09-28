CREATE TABLE rotina_treino_metrica (
    rotina_treino_id UUID NOT NULL,
    metrica_id UUID NOT NULL,
    PRIMARY KEY (rotina_treino_id, metrica_id),
    CONSTRAINT fk_rtm_rotina FOREIGN KEY (rotina_treino_id) REFERENCES rotina_treino(id) ON DELETE CASCADE,
    CONSTRAINT fk_rtm_metrica FOREIGN KEY (metrica_id) REFERENCES metrica(id) ON DELETE RESTRICT
);

CREATE INDEX idx_rtm_rotina ON rotina_treino_metrica(rotina_treino_id);
CREATE INDEX idx_rtm_metrica ON rotina_treino_metrica(metrica_id);