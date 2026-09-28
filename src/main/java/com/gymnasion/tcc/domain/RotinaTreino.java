package com.gymnasion.tcc.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "rotina_treino")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RotinaTreino {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String objetivos;

    @Column(name = "frequencia_semanal", nullable = false)
    private Integer frequenciaSemanal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modalidade_id", nullable = false)
    private Modalidade modalidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_trainer_id", nullable = false)
    private PersonalTrainer personalTrainer;

    @ManyToMany
    @JoinTable(
            name = "segue",
            joinColumns = @JoinColumn(name = "rotina_treino_id"),
            inverseJoinColumns = @JoinColumn(name = "aluno_id")
    )
    @Builder.Default
    private Set<Aluno> alunos = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "rotina_treino_metrica",
            joinColumns = @JoinColumn(name = "rotina_treino_id"),
            inverseJoinColumns = @JoinColumn(name = "metrica_id")
    )
    @Builder.Default
    private Set<Metrica> metricas = new HashSet<>();
}