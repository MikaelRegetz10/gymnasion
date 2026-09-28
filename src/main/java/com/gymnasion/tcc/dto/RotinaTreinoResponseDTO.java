package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.TipoMetrica;

import java.util.List;
import java.util.UUID;

public record RotinaTreinoResponseDTO(
        UUID id,
        String titulo,
        Long modalidadeId,
        String nomeModalidade,
        Integer frequenciaSemanal,
        String objetivos,
        List<AlunoResumoDTO> alunos,
        List<MetricaResumoDTO> metricas
) {
    public record AlunoResumoDTO(
            UUID id,
            String nome
    ) {}

    public record MetricaResumoDTO(
            UUID id,
            String titulo,
            TipoMetrica tipo
    ) {}
}