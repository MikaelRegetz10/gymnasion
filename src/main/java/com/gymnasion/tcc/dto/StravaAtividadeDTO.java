package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record StravaAtividadeDTO(
        @Schema(description = "Identificador da atividade no Strava", example = "1234567890")
        String id,

        @Schema(description = "Título da atividade", example = "Corrida Matinal no Parque")
        String nome,

        @Schema(description = "Tipo bruto da atividade no Strava (Run, Ride, Swim, etc.)", example = "Run")
        String tipo,

        @Schema(description = "Nome amigável da modalidade em português", example = "Corrida")
        String esporte,

        @Schema(description = "Data local da execução (YYYY-MM-DD)", example = "2026-10-21")
        LocalDate data,

        @Schema(description = "Data e hora de início da atividade")
        OffsetDateTime dataHora,

        @Schema(description = "Distância percorrida em metros", example = "5230.5")
        Double distanciaMetros,

        @Schema(description = "Distância percorrida em quilômetros (formato compatível com métrica NUMERICO)", example = "5.23")
        Double distanciaKm,

        @Schema(description = "Distância formatada com unidade", example = "5,23 km")
        String distanciaFormatada,

        @Schema(description = "Tempo em movimento em segundos", example = "1635")
        Integer tempoSegundos,

        @Schema(description = "Tempo formatado no padrão MM:SS (compatível com métrica TEMPO)", example = "27:15")
        String tempoFormatado,

        @Schema(description = "Duração inteira em minutos para preenchimento da sessão", example = "27")
        Integer duracaoMinutos,

        @Schema(description = "Frequência cardíaca média em bpm (se disponível no sensor/relógio)", example = "154")
        Integer frequenciaCardiacaMedia,

        @Schema(description = "Frequência cardíaca máxima em bpm", example = "175")
        Integer frequenciaCardiacaMaxima,

        @Schema(description = "Ritmo médio com unidade da modalidade: min/km (corrida e caminhada), min/100m (natação) ou km/h (ciclismo)", example = "05:13 min/km")
        String ritmoMedio,

        @Schema(description = "Tempo total decorrido formatado no padrão MM:SS ou HH:MM:SS", example = "28:10")
        String tempoTotalFormatado,

        @Schema(description = "Cadência média em passos/min (corrida) ou rotações/min (ciclismo)", example = "168.0")
        Double cadenciaMedia,

        @Schema(description = "Potência média gerada em Watts", example = "210.0")
        Double potenciaMediaWatts,

        @Schema(description = "Potência máxima gerada em Watts", example = "420.0")
        Double potenciaMaximaWatts,

        @Schema(description = "Ganho total de elevação em metros (altimetria acumulada)", example = "125.0")
        Double elevacaoGanhoMetros,

        @Schema(description = "Velocidade média em km/h", example = "11.5")
        Double velocidadeMediaKmh,

        @Schema(description = "Velocidade máxima em km/h", example = "18.2")
        Double velocidadeMaximaKmh,

        @Schema(description = "Gasto calórico estimado", example = "380.0")
        Double calorias
) {}
