package com.gymnasion.tcc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "aluno")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_id", nullable = false)
    @JsonIgnore
    private PersonalTrainer personal;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusConvite status = StatusConvite.PENDENTE;

    @ManyToMany
    @JoinTable(
            name = "praticada",
            joinColumns = @JoinColumn(name = "aluno_id"),
            inverseJoinColumns = @JoinColumn(name = "modalidade_id")
    )
    private Set<Modalidade> modalidades = new HashSet<>();
}
