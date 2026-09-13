INSERT INTO modalidade (nome, descricao)
VALUES
    -- Modalidades da US-03
    ('Natação', 'Treinos aquáticos para desenvolvimento de cardiorrespiratório, resistência e técnica de nados.'),
    ('Academia', 'Treinamento de musculação, hipertrofia, força máxima e condicionamento físico geral.'),
    ('Tênis', 'Treinamento de quadra focado em agilidade, coordenação motora, tempo de reação e cardio.'),

    -- Fitness & Funcional
    ('Crossfit', 'Treinamento funcional de alta intensidade com movimentos variados e levantamento de peso.'),
    ('Calistenia', 'Treino focado no domínio do próprio peso corporal para força, flexibilidade e controle.'),
    ('Pilates', 'Exercícios focados no fortalecimento do core, postura, flexibilidade e controle corporal.'),
    ('Yoga', 'Prática que combina posturas físicas, respiração e meditação para mobilidade e equilíbrio mental.'),
    ('Funcional', 'Exercícios baseados em padrões de movimento naturais do corpo humano para agilidade e força.'),

    -- Esportes Aquáticos
    ('Hidroginástica', 'Exercícios aeróbicos praticados em piscina de baixa intensidade articular.'),
    ('Polo Aquático', 'Esporte coletivo aquático focado em resistência tática e capacidade aeróbica.'),

    -- Combate e Artes Marciais
    ('Jiujitsu', 'Arte marcial focada em lutas de chão, imobilização e alavancas.'),
    ('Muay Thai', 'Arte marcial tailandesa de combate em pé utilizando punhos, cotovelos, joelhos e canelas.'),
    ('Boxe', 'Esporte de combate focado no trabalho de pés, esquivas e golpes de punho.'),
    ('Judo', 'Arte marcial baseada em projeções, amortecimento de quedas e controle do oponente.'),

    -- Corrida e Ciclismo
    ('Corrida de Rua', 'Treinamento focado em ritmo, endurance, ganho de VO2 máx e provas de média/longa distância.'),
    ('Ciclismo', 'Treino de alta performance em bicicleta (estrada ou mountain bike) para resistência aeróbica.'),
    ('Spinning', 'Ciclismo indoor em alta intensidade com variação de ritmo e carga.'),

    -- Esportes de Quadra e Areia
    ('Beach Tennis', 'Esporte de areia para desenvolvimento de agilidade, explosão muscular e reflexo.'),
    ('Futevôlei', 'Modalidade de areia focada em controle de bola, flexibilidade e resistência física.'),
    ('Basquete', 'Treinamento esportivo focado em explosão, salto vertical e condicionamento cardiovascular.'),
    ('Vôlei', 'Treinamento de quadra focado em impulsão, tempo de reação e coordenação motora.')
ON CONFLICT DO NOTHING;