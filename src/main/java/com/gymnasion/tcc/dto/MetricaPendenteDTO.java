package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoMetrica;

import java.time.LocalDate;
import java.util.UUID;

public record MetricaPendenteDTO(
        UUID sessaoId,
        Integer semana,
        LocalDate dataPlanejada,
        LocalDate dataReal,
        StatusSessaoTreino statusSessao,
        Boolean concluidaAluno,
        UUID metricaId,
        String tituloMetrica,
        TipoMetrica tipoMetrica,
        ResponsavelPreenchimento quemPreenche,
        Boolean obrigatoria
) {}
