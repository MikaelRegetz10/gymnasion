package com.gymnasion.tcc.domain.enums;

/**
 * Estados da sessão de treino conforme o DRS v2 (US-10 / US-21 / US-22).
 * Nasce PLANEJADA e passa a CONCLUIDA após confirmação da execução.
 */
public enum StatusSessaoTreino {
    PLANEJADA,
    CONCLUIDA
}
