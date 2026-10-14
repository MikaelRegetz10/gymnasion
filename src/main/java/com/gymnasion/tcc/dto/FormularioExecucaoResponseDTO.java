package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FormularioExecucaoResponseDTO(
        @Schema(description = "Dados da sessão de treino, incluindo o registro já confirmado, se houver")
        SessaoTreinoResponseDTO sessao,

        @Schema(description = "Indica se o aluno ainda pode registrar a execução (false quando a execução já foi confirmada)", example = "true")
        Boolean registroPermitido,

        @Schema(description = "Métricas configuradas pelo personal para o treinamento, com tipo, obrigatoriedade e responsável pelo preenchimento")
        List<RotinaTreinoResponseDTO.MetricaResumoDTO> metricas
) {}
