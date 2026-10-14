package com.gymnasion.tcc.domain;

import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sessao_treino")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessaoTreino {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rotina_treino_id", nullable = false)
    private RotinaTreino rotinaTreino;

    @Column(name = "semana", nullable = false)
    @Builder.Default
    private Integer semana = 1;

    @Column(name = "data_planejada", nullable = false)
    private LocalDate dataPlanejada;

    @Column(name = "data_real")
    private LocalDate dataReal;

    @Column
    private Integer duracao; // em minutos

    @Column(columnDefinition = "TEXT")
    private String atividades;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSessaoTreino status = StatusSessaoTreino.PLANEJADA;

    @Builder.Default
    @Column(name = "concluida_aluno", nullable = false)
    private Boolean concluidaAluno = false;

    @Builder.Default
    @Column(name = "validada_personal", nullable = false)
    private Boolean validadaPersonal = false;

    @OneToOne(mappedBy = "sessaoTreino", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Desempenho desempenho;
}