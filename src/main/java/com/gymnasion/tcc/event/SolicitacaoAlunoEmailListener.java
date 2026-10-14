package com.gymnasion.tcc.event;

import com.gymnasion.tcc.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia automaticamente o e-mail ao aluno (US-03 C3/C4) somente após o commit
 * da aprovação/recusa, em thread separada, para não bloquear a resposta HTTP
 * nem enviar e-mail caso a transação seja revertida.
 */
@Component
@RequiredArgsConstructor
public class SolicitacaoAlunoEmailListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSolicitacaoAvaliada(SolicitacaoAlunoAvaliadaEvent event) {
        if (event.emailAluno() == null || event.emailAluno().isBlank()) {
            return;
        }

        if (event.aprovado()) {
            emailService.notificarAprovacaoAluno(event.emailAluno(), event.nomePersonal());
        } else {
            emailService.notificarRecusaAluno(event.emailAluno(), event.nomePersonal());
        }
    }
}
