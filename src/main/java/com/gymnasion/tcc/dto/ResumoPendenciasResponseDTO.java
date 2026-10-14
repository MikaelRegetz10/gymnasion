package com.gymnasion.tcc.dto;

import java.util.List;
import java.util.UUID;

public record ResumoPendenciasResponseDTO(
        Integer totalAlunosComPendencias,
        Integer totalMetricasPendentes,
        List<AlunoPendenciaItemDTO> alunos
) {
    public record AlunoPendenciaItemDTO(
            UUID alunoId,
            String nomeAluno,
            UUID rotinaId,
            String tituloRotina,
            Integer quantidadePendencias
    ) {}
}
