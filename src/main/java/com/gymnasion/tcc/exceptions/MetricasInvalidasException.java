package com.gymnasion.tcc.exceptions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lançada quando métricas obrigatórias não foram preenchidas ou quando valores não respeitam o tipo configurado (US-22 C3 / C9 / C10).
 * Os campos inválidos são identificados pelo ID da métrica, com a mensagem de correção correspondente.
 */
public class MetricasInvalidasException extends BusinessException {

    private final Map<String, String> camposInvalidos;

    public MetricasInvalidasException(Map<String, String> camposInvalidos) {
        super("Existem métricas obrigatórias não preenchidas ou com valores inválidos. Corrija os campos indicados antes de confirmar o registro.");
        this.camposInvalidos = Collections.unmodifiableMap(new LinkedHashMap<>(camposInvalidos));
    }

    public Map<String, String> getCamposInvalidos() {
        return camposInvalidos;
    }
}
