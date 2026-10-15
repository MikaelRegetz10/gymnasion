package com.gymnasion.tcc.domain;

import com.gymnasion.tcc.domain.enums.TipoRegistroPerfil;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de observação ou feedback no perfil do aluno (US-26).
 * Registros são imutáveis após a criação (sem edição e sem exclusão - US-26 C5).
 */
@Entity
@Table(name = "observacao_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObservacaoFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TipoRegistroPerfil tipo;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String conteudo;

    @Builder.Default
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao = OffsetDateTime.now();
}
