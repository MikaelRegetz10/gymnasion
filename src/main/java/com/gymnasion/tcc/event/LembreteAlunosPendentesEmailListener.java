package com.gymnasion.tcc.event;

import com.gymnasion.tcc.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia o e-mail de lembrete de alunos pendentes ao personal após o commit, de forma assíncrona.
 */
@Component
@RequiredArgsConstructor
public class LembreteAlunosPendentesEmailListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLembrete(LembreteAlunosPendentesEvent event) {
        if (event.emailPersonal() == null || event.emailPersonal().isBlank()) {
            return;
        }
        emailService.notificarAlunosPendentes(
                event.emailPersonal(),
                event.nomePersonal(),
                event.nomesAlunos(),
                event.tempoLimiteFormatado()
        );
    }
}
