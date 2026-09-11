package com.gymnasion.tcc.domain;

import com.gymnasion.tcc.domain.Modalidade;
import com.gymnasion.tcc.domain.Usuario;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "personal_trainer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalTrainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modalidade_id")
    private Modalidade modalidade;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
}