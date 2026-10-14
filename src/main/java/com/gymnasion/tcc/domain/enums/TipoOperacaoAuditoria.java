package com.gymnasion.tcc.domain.enums;

/**
 * Tipo de operação registrada na auditoria do desempenho (US-23 notas técnicas).
 * - INCLUSAO: preenchimento de um valor que ainda não existia (ex.: métrica pendente do personal).
 * - CORRECAO: alteração de um valor já registrado.
 */
public enum TipoOperacaoAuditoria {
    INCLUSAO,
    CORRECAO
}
