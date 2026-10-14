package com.gymnasion.tcc.dto;

import java.util.List;
import java.util.UUID;

public record AcompanhamentoAlunoResponseDTO(
        UUID alunoId,
        String nomeAluno,
        String emailAluno,
        UUID rotinaAtivaId,
        String tituloRotinaAtiva,
        Long modalidadeId,
        String nomeModalidade,
        Integer quantidadeMetricasPendentes,
        Integer totalSessoesPlanejadas,
        Integer totalSessoesConcluidas,
        List<MetricaPendenteDTO> todasMetricasPendentes,
        List<SessaoAcompanhamentoDTO> sessoes
) {}
