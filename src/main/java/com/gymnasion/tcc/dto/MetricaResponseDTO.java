package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import java.util.UUID;

public record MetricaResponseDTO(
        UUID id,
        String titulo,
        TipoMetrica tipo,
        Long modalidadeId,
        String nomeModalidade,
        Boolean obrigatoria,
        ResponsavelPreenchimento quemPreenche,
        Boolean ativo,
        Long quantidadeAlunosComRegistros
) {}