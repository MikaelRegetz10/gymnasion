package com.gymnasion.tcc.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "termo_aceite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermoAceite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 20)
    private String versao;

    @Column(name = "data_aceite", nullable = false)
    private OffsetDateTime dataAceite;

    @PrePersist
    protected void onCreate() {
        this.dataAceite = OffsetDateTime.now();
    }
}
