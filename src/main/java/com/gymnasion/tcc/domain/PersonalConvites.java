package com.gymnasion.tcc.domain;

import com.gymnasion.tcc.domain.enums.StatusConvite;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "personal_convites")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PersonalConvites {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "personal_id", nullable = false)
    private UUID personalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modalidade_id", nullable = false)
    private Modalidade modalidade;

    @Column(nullable = false, length = 20)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private StatusConvite status = StatusConvite.ATIVO;

    @Column(name = "maximo_usuarios")
    @Builder.Default
    private Integer maximoUsuarios = 30;

    @Column(name = "quantidades_usuarios")
    @Builder.Default
    private Integer quantidadesUsuarios = 0;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    @CreationTimestamp
    @Column(name = "data_criacao", updatable = false)
    private LocalDateTime dataCriacao;
}