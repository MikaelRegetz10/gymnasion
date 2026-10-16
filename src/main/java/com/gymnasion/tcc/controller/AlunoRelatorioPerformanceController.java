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

@RestController
@RequestMapping("/alunos/relatorio")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ALUNO')")
@Tag(name = "Aluno - Relatório de Performance (US-16)", description = "Emissão do próprio relatório de performance em PDF pelo aluno")
@SecurityRequirement(name = "bearerAuth")
public class AlunoRelatorioPerformanceController {

    private final RelatorioPerformanceService relatorioPerformanceService;

    @GetMapping
    @Operation(
            summary = "Gerar o próprio relatório de performance em PDF (US-16 C1, C3)",
            description = "Permite ao aluno autenticado gerar o próprio relatório de performance no período selecionado, com dados tabulados e gráficos. Quando o período não possui registros, retorna 422 com a mensagem 'Não há dados para o período selecionado' e o campo periodoSugerido (US-16 C3)."
    )
    @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso.", content = @Content(mediaType = "application/pdf"))
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado.")
    @ApiResponse(responseCode = "422", description = "Aluno inativo (RN-014), período sem dados (com período sugerido), período não informado ou intervalo de datas inválido.")
    public ResponseEntity<byte[]> gerarProprioRelatorio(
            @AuthenticationPrincipal Usuario usuario,
            @Parameter(description = "ID opcional da modalidade para filtrar")
            @RequestParam(value = "modalidadeId", required = false) Long modalidadeId,
            @Parameter(description = "Data de início do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data de fim do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloAluno(
                usuario, modalidadeId, dataInicio, dataFim
        );
        return RelatorioPdfResponse.of(relatorio);
    }
}
