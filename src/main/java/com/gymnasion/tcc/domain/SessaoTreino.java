package com.gymnasion.tcc.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "sessao_treino")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessaoTreino {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rotina_treino_id", nullable = false)
    private RotinaTreino rotinaTreino;

    @Column(name = "data_execucao", nullable = false)
    private LocalDate dataExecucao;

    @Column
    private Integer duracao; // em minutos
}