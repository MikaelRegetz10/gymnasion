package com.gymnasion.tcc.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "registro_metrica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroMetrica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desempenho_id", nullable = false)
    private Desempenho desempenho;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metrica_id", nullable = false)
    private Metrica metrica;

    @Column(nullable = false, length = 255)
    private String valor;
}