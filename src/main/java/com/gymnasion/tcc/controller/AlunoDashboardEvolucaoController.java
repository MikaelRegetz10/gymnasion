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

@RestController
@RequestMapping("/alunos/evolucao")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ALUNO')")
@Tag(name = "Aluno - Dashboards de Evolução (US-15 C3)", description = "Visualização da própria evolução e esforço pelo aluno, com indicadores de melhoria e regressão")
@SecurityRequirement(name = "bearerAuth")
public class AlunoDashboardEvolucaoController {

    private final DashboardEvolucaoService dashboardEvolucaoService;

    @GetMapping
    @Operation(
            summary = "Consultar próprio dashboard dinâmico de evolução (US-15 C2, C3 / US-20 C3)",
            description = "Permite ao aluno autenticado visualizar seus próprios gráficos de linha de evolução temporal das métricas da modalidade e esforço percebido (RPE Borg CR-10), com indicadores visuais de melhoria ou regressão (US-15 C3). Suporta filtros opcionais por modalidade e período."
    )
    @ApiResponse(responseCode = "200", description = "Dashboard de evolução retornado com sucesso.")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado.")
    @ApiResponse(responseCode = "422", description = "Aluno inativo (RN-014) ou intervalo de datas inválido.")
    public ResponseEntity<DashboardEvolucaoResponseDTO> obterProprioDashboard(
            @AuthenticationPrincipal Usuario usuario,
            @Parameter(description = "ID opcional da modalidade para filtrar")
            @RequestParam(value = "modalidadeId", required = false) Long modalidadeId,
            @Parameter(description = "Data de início do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data de fim do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        DashboardEvolucaoResponseDTO response = dashboardEvolucaoService.obterDashboardPeloAluno(
                usuario, modalidadeId, dataInicio, dataFim
        );
        return ResponseEntity.ok(response);
    }
}
