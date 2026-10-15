package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

public record CorrigirDesempenhoRequestDTO(
        @Schema(description = "Nova data real de execução (a data planejada é preservada). Omitir para não alterar", example = "2026-10-23")
        LocalDate dataReal,

        @Schema(description = "Novas observações do desempenho. Omitir para não alterar", example = "Aluno relatou dor no ombro na última série.")
        String observacoes,

        @Valid
        @Schema(description = "Métricas a corrigir (valores já registrados) ou a incluir (métricas pendentes do personal). Omitir as que não serão alteradas")
        List<RegistrarResultadoMetricaDTO> metricas
) {}
