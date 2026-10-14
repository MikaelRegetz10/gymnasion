package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegistrarExecucaoSessaoRequestDTO(
        @NotNull(message = "A data real de execução é obrigatória.")
        @Schema(description = "Data real em que o treino foi executado (preserva a data planejada)", example = "2026-10-21")
        LocalDate dataReal,

        @Min(value = 1, message = "A duração real deve ser no mínimo 1 minuto.")
        @Schema(description = "Duração real da execução em minutos", example = "55")
        Integer duracao,

        @Min(value = 1, message = "O nível de esforço (RPE) deve estar entre 1 e 10.")
        @Max(value = 10, message = "O nível de esforço (RPE) deve estar entre 1 e 10.")
        @Schema(description = "Nível de esforço/cansaço na escala de Borg CR-10 (RN-018), de 1 a 10 (opcional)", example = "7")
        Integer nivelCansaco,

        @Schema(description = "ID do aluno que executou a sessão (obrigatório se o personal trainer estiver registrando)", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        UUID alunoId,

        @NotEmpty(message = "É necessário informar pelo menos um resultado de métrica.")
        @Valid
        @Schema(description = "Lista com os resultados das métricas registradas na sessão")
        List<RegistrarResultadoMetricaDTO> resultados
) {}
