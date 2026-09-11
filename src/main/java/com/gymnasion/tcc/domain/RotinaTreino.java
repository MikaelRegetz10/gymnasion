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

    @Column(nullable = false, length = 100)
    private String titulo;

    @Column(length = 255)
    private String objetivo;

    @Column(name = "frequencia_semanal")
    private Integer frequenciaSemanal;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_trainer_id", nullable = false)
    private PersonalTrainer personalTrainer;

    @ManyToMany
    @JoinTable(
            name = "segue",
            joinColumns = @JoinColumn(name = "rotina_treino_id"),
            inverseJoinColumns = @JoinColumn(name = "aluno_id")
    )
    private Set<Aluno> alunos = new HashSet<>();
}
