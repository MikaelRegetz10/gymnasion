package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO;
import com.gymnasion.tcc.service.DashboardEvolucaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers/alunos/{alunoId}/evolucao")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Dashboards de Evolução (US-15)", description = "Agregação de dados e séries temporais para gráficos dinâmicos de desempenho e esforço do aluno")
@SecurityRequirement(name = "bearerAuth")
public class DashboardEvolucaoController {

    private final DashboardEvolucaoService dashboardEvolucaoService;

    @GetMapping
    @Operation(
            summary = "Consultar dashboard dinâmico de evolução do aluno (US-15 C1, C2 / US-20 C3)",
            description = "Retorna os dados agregados para renderização dos gráficos de linha de evolução temporal das métricas da modalidade e esforço percebido (RPE Borg CR-10). Exige ao menos 3 sessões registradas para exibir os gráficos. Suporta filtros opcionais por modalidade e período (dataInicio e dataFim) com tempo de resposta inferior a 3 segundos (US-15 C2)."
    )
    @ApiResponse(responseCode = "200", description = "Dados do dashboard de evolução retornados com sucesso.")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado ou não pertence a este Personal Trainer.")
    @ApiResponse(responseCode = "422", description = "Data de início posterior à data de fim ou parâmetros inconsistentes.")
    public ResponseEntity<DashboardEvolucaoResponseDTO> obterDashboardAluno(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId,
            @Parameter(description = "ID opcional da modalidade para filtrar")
            @RequestParam(value = "modalidadeId", required = false) Long modalidadeId,
            @Parameter(description = "Data de início do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data de fim do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        DashboardEvolucaoResponseDTO response = dashboardEvolucaoService.obterDashboardPeloPersonal(
                usuario, alunoId, modalidadeId, dataInicio, dataFim
        );
        return ResponseEntity.ok(response);
    }
}
