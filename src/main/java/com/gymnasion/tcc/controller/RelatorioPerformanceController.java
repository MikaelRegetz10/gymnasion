package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.RelatorioPdfDTO;
import com.gymnasion.tcc.service.RelatorioPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
@RequestMapping("/personal-trainers/alunos/{alunoId}/relatorio")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Relatório de Performance (US-16)", description = "Emissão do relatório de performance do aluno em PDF, com dados tabulados e gráficos")
@SecurityRequirement(name = "bearerAuth")
public class RelatorioPerformanceController {

    private final RelatorioPerformanceService relatorioPerformanceService;

    @GetMapping
    @Operation(
            summary = "Gerar relatório de performance do aluno em PDF (US-16 C1, C3)",
            description = "Gera o relatório de performance do aluno no período selecionado, com resumo de adesão, sessões (data planejada e data real), métricas tabuladas com gráficos de evolução e esforço percebido (RPE). Métricas descontinuadas permanecem identificadas (RN-017). Sem modalidadeId, o relatório traz uma seção por modalidade com registros no período. Quando o período não possui registros, retorna 422 com a mensagem 'Não há dados para o período selecionado' e o campo periodoSugerido (US-16 C3)."
    )
    @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso.", content = @Content(mediaType = "application/pdf"))
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado ou não pertence a este Personal Trainer.")
    @ApiResponse(responseCode = "422", description = "Período sem dados (com período sugerido), período não informado ou data de início posterior à data de fim.")
    public ResponseEntity<byte[]> gerarRelatorio(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId,
            @Parameter(description = "ID opcional da modalidade para filtrar")
            @RequestParam(value = "modalidadeId", required = false) Long modalidadeId,
            @Parameter(description = "Data de início do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data de fim do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(
                usuario, alunoId, modalidadeId, dataInicio, dataFim
        );
        return RelatorioPdfResponse.of(relatorio);
    }
}
