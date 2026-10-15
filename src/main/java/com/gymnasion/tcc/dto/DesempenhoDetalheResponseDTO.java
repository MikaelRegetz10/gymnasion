package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DesempenhoDetalheResponseDTO(
        UUID sessaoId,
        UUID rotinaTreinoId,
        UUID desempenhoId,
        UUID alunoId,
        String nomeAluno,
        Integer semana,
        LocalDate dataPlanejada,
        LocalDate dataReal,
        StatusSessaoTreino status,
        Boolean concluidaAluno,
        Boolean validadaPersonal,
        Integer nivelCansaco,
        String registradoPor,
        OffsetDateTime dataRegistro,
        String observacoes,
        @Schema(description = "Métricas do registro, com o valor atual e as ações permitidas ao personal")
        List<MetricaDesempenhoDTO> metricas
) {
    public record MetricaDesempenhoDTO(
            UUID metricaId,
            String titulo,
            TipoMetrica tipo,
            Boolean obrigatoria,
            ResponsavelPreenchimento quemPreenche,
            @Schema(description = "false quando a métrica foi descontinuada (RN-017)")
            Boolean ativa,
            @Schema(description = "Valor registrado; null quando ainda não preenchida")
            String valor,
            @Schema(description = "O valor registrado pode ser corrigido (US-23 C2)")
            Boolean correcaoPermitida,
            @Schema(description = "A métrica está pendente e pode ser preenchida pelo personal (US-23 C7)")
            Boolean inclusaoPermitida
    ) {}
}
