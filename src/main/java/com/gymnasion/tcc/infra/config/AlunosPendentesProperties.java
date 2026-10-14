package com.gymnasion.tcc.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuração do lembrete automático de alunos pendentes de aprovação.
 *
 * @param enabled           liga/desliga o job
 * @param tempoLimite       tempo (X) que um aluno pode ficar pendente antes de gerar lembrete (ex.: 24h, 30m, 2d)
 * @param intervaloLembrete intervalo mínimo entre dois lembretes para o mesmo personal
 * @param cron              frequência de verificação (cron do Spring, 6 campos)
 */
@ConfigurationProperties(prefix = "app.notificacoes.alunos-pendentes")
public record AlunosPendentesProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("24h") Duration tempoLimite,
        @DefaultValue("24h") Duration intervaloLembrete,
        @DefaultValue("0 0 * * * *") String cron
) {
}
