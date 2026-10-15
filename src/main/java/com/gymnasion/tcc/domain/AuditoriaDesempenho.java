package com.gymnasion.tcc.domain;

import com.gymnasion.tcc.domain.enums.CampoAuditado;
import com.gymnasion.tcc.domain.enums.TipoOperacaoAuditoria;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auditoria_desempenho")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditoriaDesempenho {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desempenho_id", nullable = false)
    private Desempenho desempenho;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao", nullable = false, length = 20)
    private TipoOperacaoAuditoria tipoOperacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampoAuditado campo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metrica_id")
    private Metrica metrica;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_novo", nullable = false, columnDefinition = "TEXT")
    private String valorNovo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "data_hora", nullable = false)
    private OffsetDateTime dataHora;

    @PrePersist
    protected void onCreate() {
        if (this.dataHora == null) {
            this.dataHora = OffsetDateTime.now();
        }
    }
}
