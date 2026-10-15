package com.gymnasion.tcc.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "desempenho")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Desempenho {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_treino_id", nullable = false, unique = true)
    private SessaoTreino sessaoTreino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @Column(name = "registrado_por", length = 50)
    private String registradoPor; // ALUNO ou PERSONAL_TRAINER

    @Column(name = "data_registro", nullable = false)
    private OffsetDateTime dataRegistro;

    @Column(name = "nivel_cansaco")
    private Integer nivelCansaco; // 1 a 10 (Escala Borg CR-10, RN-018)

    @Column(columnDefinition = "TEXT")
    private String observacoes; // US-23 C4: observações do desempenho, corrigíveis pelo personal

    @OneToMany(mappedBy = "desempenho", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<RegistroMetrica> registrosMetricas = new java.util.ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.dataRegistro == null) {
            this.dataRegistro = OffsetDateTime.now();
        }
    }
}
