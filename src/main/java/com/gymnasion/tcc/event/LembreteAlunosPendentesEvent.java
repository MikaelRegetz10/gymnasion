package com.gymnasion.tcc.event;

import java.util.List;

/**
 * Publicado pelo job de lembrete quando um personal possui alunos pendentes
 * há mais que o tempo limite configurado. Consumido após o commit para envio de e-mail.
 */
public record LembreteAlunosPendentesEvent(
        String emailPersonal,
        String nomePersonal,
        List<String> nomesAlunos,
        String tempoLimiteFormatado
) {
}
