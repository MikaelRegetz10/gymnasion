package com.gymnasion.tcc.domain.enums;

/**
 * Mapeamento de campos retornados pela API v3 do Strava para métricas personalizadas (US-14 / US-18).
 */
public enum CampoStrava {
    DISTANCIA_KM,
    DISTANCIA_METROS,
    TEMPO_MOVIMENTO,
    TEMPO_TOTAL,
    DURACAO_MINUTOS,
    VELOCIDADE_MEDIA_KMH,
    VELOCIDADE_MAXIMA_KMH,
    RITMO_MEDIO,
    FREQUENCIA_CARDIACA_MEDIA,
    FREQUENCIA_CARDIACA_MAXIMA,
    CADENCIA_MEDIA,
    POTENCIA_MEDIA_WATTS,
    POTENCIA_MAXIMA_WATTS,
    ELEVACAO_GANHO_METROS,
    CALORIAS,
    NENHUM;

    public boolean isCompativelCom(TipoMetrica tipo) {
        if (this == NENHUM || tipo == null) {
            return true;
        }
        return switch (this) {
            case DISTANCIA_KM, DISTANCIA_METROS, DURACAO_MINUTOS,
                 VELOCIDADE_MEDIA_KMH, VELOCIDADE_MAXIMA_KMH,
                 CADENCIA_MEDIA, POTENCIA_MEDIA_WATTS, POTENCIA_MAXIMA_WATTS,
                 ELEVACAO_GANHO_METROS, CALORIAS -> tipo == TipoMetrica.NUMERICO;
            case TEMPO_MOVIMENTO, TEMPO_TOTAL -> tipo == TipoMetrica.TEMPO;
            case FREQUENCIA_CARDIACA_MEDIA, FREQUENCIA_CARDIACA_MAXIMA -> tipo == TipoMetrica.FREQUENCIA_CARDIACA;
            case RITMO_MEDIO -> tipo == TipoMetrica.TEXTO || tipo == TipoMetrica.NUMERICO;
            default -> true;
        };
    }
}
