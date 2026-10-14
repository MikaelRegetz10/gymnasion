package com.gymnasion.tcc.domain.enums;

/**
 * Responsável pelo preenchimento da métrica durante o registro de treinos (US-18).
 * - ALUNO: Preenchida apenas pelo aluno.
 * - PERSONAL: Exclusiva do Personal Trainer; aluno não tem permissão de registrar/alterar (US-18 C5).
 * - AMBOS: Pode ser preenchida por ambos.
 */
public enum ResponsavelPreenchimento {
    ALUNO,
    PERSONAL,
    AMBOS
}
