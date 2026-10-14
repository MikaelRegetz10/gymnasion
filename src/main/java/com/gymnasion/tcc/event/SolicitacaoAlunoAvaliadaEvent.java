package com.gymnasion.tcc.event;

/**
 * US-03 C3/C4: Evento publicado quando o Personal Trainer aprova ou recusa
 * a solicitação de cadastro de um aluno. Consumido após o commit da transação
 * para envio automático de e-mail ao aluno.
 */
public record SolicitacaoAlunoAvaliadaEvent(
        String emailAluno,
        String nomeAluno,
        String nomePersonal,
        boolean aprovado
) {
}
