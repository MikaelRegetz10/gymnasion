package com.gymnasion.tcc.exceptions;

/**
 * Lançada quando o login de uma conta é bloqueado temporariamente por excesso de tentativas inválidas (RN-010 / US-02).
 * Bloqueia o acesso após 5 tentativas inválidas consecutivas por 5 minutos.
 */
public class ContaBloqueadaException extends BusinessException {

    private final int tentativas;
    private final int minutosBloqueio;

    public ContaBloqueadaException() {
        this("Conta temporariamente bloqueada após 5 tentativas inválidas. Tente novamente em 5 minutos.", 5, 5);
    }

    public ContaBloqueadaException(String message) {
        this(message, 5, 5);
    }

    public ContaBloqueadaException(String message, int tentativas, int minutosBloqueio) {
        super(message);
        this.tentativas = tentativas;
        this.minutosBloqueio = minutosBloqueio;
    }

    public int getTentativas() {
        return tentativas;
    }

    public int getMinutosBloqueio() {
        return minutosBloqueio;
    }
}
