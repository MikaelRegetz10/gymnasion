package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import java.time.LocalDate;
import java.util.UUID;

public record SessaoTreinoResponseDTO(
        UUID id,
        UUID rotinaTreinoId,
        Integer semana,
        LocalDate dataPlanejada,
        LocalDate dataReal,
        Integer duracao,
        String atividades,
        StatusSessaoTreino status,
        Boolean concluidaAluno,
        Boolean validadaPersonal,
        DesempenhoResponseDTO desempenho
) {}
