package com.gymnasion.tcc.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "integracao_strava")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegracaoStrava {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id", nullable = false, unique = true)
    private Aluno aluno;

    @Column(name = "access_token", nullable = false, length = 255)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, length = 255)
    private String refreshToken;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;
}