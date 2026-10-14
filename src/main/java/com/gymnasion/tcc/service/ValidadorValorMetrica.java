package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Metrica;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Validação dos valores de métricas conforme o tipo (RN-015 / US-18 notas técnicas).
 * Utilizada no registro da execução (US-22) e na correção de desempenho (US-23).
 */
public final class ValidadorValorMetrica {

    // US-18 notas técnicas: "Tempo" valida formato mm:ss (segundos de 00 a 59)
    private static final Pattern FORMATO_TEMPO = Pattern.compile("^\\d{2,}:[0-5]\\d$");
    // US-18 notas técnicas: "Frequência cardíaca" valida faixa numérica plausível (em bpm)
    private static final int FREQUENCIA_CARDIACA_MINIMA = 30;
    private static final int FREQUENCIA_CARDIACA_MAXIMA = 250;
    // Limite da coluna registro_metrica.valor
    private static final int TAMANHO_MAXIMO_VALOR_METRICA = 255;

    private ValidadorValorMetrica() {
    }

    /**
     * Valida o valor informado conforme o tipo da métrica.
     * Retorna a mensagem de erro, ou null quando o valor é válido.
     */
    public static String validar(Metrica metrica, String valor) {
        String titulo = metrica.getTitulo();
        if (valor.length() > TAMANHO_MAXIMO_VALOR_METRICA) {
            return "O valor da métrica '" + titulo + "' deve ter no máximo " + TAMANHO_MAXIMO_VALOR_METRICA + " caracteres.";
        }

        return switch (metrica.getTipo()) {
            case NUMERICO -> {
                try {
                    new BigDecimal(valor.replace(',', '.'));
                    yield null;
                } catch (NumberFormatException e) {
                    yield "O valor da métrica '" + titulo + "' deve ser numérico.";
                }
            }
            case TEMPO -> FORMATO_TEMPO.matcher(valor).matches()
                    ? null
                    : "O valor da métrica '" + titulo + "' deve estar no formato mm:ss.";
            case FREQUENCIA_CARDIACA -> {
                try {
                    int bpm = Integer.parseInt(valor);
                    yield bpm >= FREQUENCIA_CARDIACA_MINIMA && bpm <= FREQUENCIA_CARDIACA_MAXIMA
                            ? null
                            : "O valor da métrica '" + titulo + "' deve estar entre " + FREQUENCIA_CARDIACA_MINIMA + " e " + FREQUENCIA_CARDIACA_MAXIMA + " bpm.";
                } catch (NumberFormatException e) {
                    yield "O valor da métrica '" + titulo + "' deve ser um número inteiro de batimentos por minuto (bpm).";
                }
            }
            case TEXTO -> null;
        };
    }
}
