package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SessaoAcompanhamentoDTO(
        UUID sessaoId,
        Integer semana,
        LocalDate dataPlanejada,
        LocalDate dataReal,
        Integer duracao,
        String atividades,
        StatusSessaoTreino status,
        Boolean concluidaAluno,
        Boolean validadaPersonal,
        Boolean temPendenciasPersonal,
        Integer quantidadePendenciasPersonal,
        List<MetricaPendenteDTO> metricasPendentes,
        DesempenhoResponseDTO desempenho
) {}
