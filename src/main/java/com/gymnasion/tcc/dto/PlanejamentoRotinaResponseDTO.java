package com.gymnasion.tcc.dto;

import java.util.List;
import java.util.UUID;

public record PlanejamentoRotinaResponseDTO(
        UUID rotinaId,
        String tituloRotina,
        Long modalidadeId,
        String nomeModalidade,
        Integer frequenciaSemanal,
        String objetivos,
        List<SemanaPlanejamentoDTO> semanas
) {
    public record SemanaPlanejamentoDTO(
            Integer numeroSemana,
            List<SessaoTreinoResponseDTO> sessoes
    ) {}
}
