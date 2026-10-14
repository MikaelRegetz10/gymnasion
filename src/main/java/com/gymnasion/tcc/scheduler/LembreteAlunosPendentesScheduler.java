package com.gymnasion.tcc.scheduler;

import com.gymnasion.tcc.service.LembreteAlunosPendentesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job periódico do lembrete de alunos pendentes de aprovação.
 * Frequência configurada em {@code app.notificacoes.alunos-pendentes.cron}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LembreteAlunosPendentesScheduler {

    private final LembreteAlunosPendentesService lembreteService;

    @Scheduled(cron = "${app.notificacoes.alunos-pendentes.cron:0 0 * * * *}")
    public void executar() {
        try {
            lembreteService.processarLembretes();
        } catch (Exception e) {
            log.error("Falha ao processar lembretes de alunos pendentes: {}", e.getMessage(), e);
        }
    }
}
