package com.gymnasion.tcc.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(
        name = "metrica",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_metrica_titulo_personal_modalidade",
                columnNames = {"titulo", "personal_trainer_id", "modalidade_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Metrica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String titulo;

    @Column(nullable = false, length = 50)
    private String tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modalidade_id", nullable = false)
    private Modalidade modalidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_trainer_id", nullable = false)
    private PersonalTrainer personalTrainer;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
}
