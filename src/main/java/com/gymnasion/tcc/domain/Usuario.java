package com.gymnasion.tcc.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "ultimo_acesso")
    private OffsetDateTime ultimoAcesso;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @Column(nullable = false, length = 13)
    private String celular;

    @PrePersist
    protected void onCreate() {
        this.dataCriacao = OffsetDateTime.now();
    }
}