package com.gymnasion.tcc.exceptions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lançada quando métricas obrigatórias não foram preenchidas ou quando valores não respeitam o tipo configurado (US-13 C2 / US-22 C3 / C9 / C10 / US-23 C6 / RN-015).
 * Os campos inválidos são identificados pelo ID da métrica, com a mensagem de correção correspondente.
 */
public class MetricasInvalidasException extends BusinessException {

    private final Map<String, String> camposInvalidos;

    public MetricasInvalidasException(Map<String, String> camposInvalidos) {
        this(gerarMensagem(camposInvalidos), camposInvalidos);
    }

    public MetricasInvalidasException(String message, Map<String, String> camposInvalidos) {
        super(message);
        this.camposInvalidos = Collections.unmodifiableMap(new LinkedHashMap<>(camposInvalidos));
    }

    private static String gerarMensagem(Map<String, String> camposInvalidos) {
        if (camposInvalidos != null && camposInvalidos.size() == 1) {
            return camposInvalidos.values().iterator().next();
        }
        return "Existem métricas obrigatórias não preenchidas ou com valores inválidos. Corrija os campos indicados antes de confirmar o registro.";
    }

    public Map<String, String> getCamposInvalidos() {
        return camposInvalidos;
    }
}
