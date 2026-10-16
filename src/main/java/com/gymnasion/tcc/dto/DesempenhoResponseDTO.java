package com.gymnasion.tcc.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DesempenhoResponseDTO(
        UUID id,
        OffsetDateTime dataRegistro,
        Integer nivelCansaco,
        String registradoPor,
        UUID alunoId,
        String nomeAluno,
        String observacoes,
        List<RegistroMetricaResponseDTO> metricas
) {
    public DesempenhoResponseDTO(
            UUID id,
            OffsetDateTime dataRegistro,
            Integer nivelCansaco,
            String registradoPor,
            UUID alunoId,
            String nomeAluno,
            List<RegistroMetricaResponseDTO> metricas
    ) {
        this(id, dataRegistro, nivelCansaco, registradoPor, alunoId, nomeAluno, null, metricas);
    }
}
