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

    @Column(name = "data_registro", nullable = false)
    private OffsetDateTime dataRegistro;

    @Column(name = "nivel_cansaco")
    private Integer nivelCansaco; // 1 a 10 (Escala de Borg)

    @PrePersist
    protected void onCreate() {
        this.dataRegistro = OffsetDateTime.now();
    }
}
