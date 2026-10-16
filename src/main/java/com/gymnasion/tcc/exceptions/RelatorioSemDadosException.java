package com.gymnasion.tcc.exceptions;

import java.time.LocalDate;

/**
 * Lançada quando o período selecionado para o relatório não possui registros (US-16 C3).
 * Carrega o intervalo alternativo com registros disponíveis, ou null quando o aluno não possui nenhum registro.
 */
public class RelatorioSemDadosException extends BusinessException {

    private final LocalDate dataInicioSugerida;
    private final LocalDate dataFimSugerida;

    public RelatorioSemDadosException(LocalDate dataInicioSugerida, LocalDate dataFimSugerida) {
        super("Não há dados para o período selecionado");
        this.dataInicioSugerida = dataInicioSugerida;
        this.dataFimSugerida = dataFimSugerida;
    }

    public LocalDate getDataInicioSugerida() {
        return dataInicioSugerida;
    }

    public LocalDate getDataFimSugerida() {
        return dataFimSugerida;
    }

    public boolean possuiPeriodoSugerido() {
        return dataInicioSugerida != null && dataFimSugerida != null;
    }
}
