package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.ConteudoRelatorio;
import com.gymnasion.tcc.dto.DisponibilidadeRelatorioDTO;
import com.gymnasion.tcc.dto.FiltroRelatorioDTO;
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
import java.util.Set;
import java.util.UUID;

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
            description = "Permite ao aluno autenticado gerar o próprio relatório de performance no período selecionado, com dados tabulados e gráficos. Quando o período não possui registros, retorna 422 com a mensagem 'Não há dados para o período selecionado' e o campo periodoSugerido (US-16 C3). As escolhas opcionais datas, metricaIds e conteudos limitam as datas, as métricas e os blocos do PDF; sem elas, o relatório sai completo. Com métricas escolhidas, entram apenas as modalidades que têm alguma delas no período, e o resumo e a lista de sessões continuam considerando todas as sessões dessas modalidades."
    )
    @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso.", content = @Content(mediaType = "application/pdf"))
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado ou métrica sem registros do aluno.")
    @ApiResponse(responseCode = "422", description = "Aluno inativo (RN-014), período sem dados (com período sugerido), período não informado ou intervalo de datas inválido.")
    public ResponseEntity<byte[]> gerarProprioRelatorio(
            @AuthenticationPrincipal Usuario usuario,
            @Parameter(description = "ID opcional da modalidade para filtrar")
            @RequestParam(value = "modalidadeId", required = false) Long modalidadeId,
            @Parameter(description = "Data de início do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data de fim do período (formato YYYY-MM-DD)")
            @RequestParam(value = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @Parameter(description = "Datas que entram no relatório, dentro do período (repita o parâmetro ou separe por vírgula, formato YYYY-MM-DD). Sem valor, entram todas as datas do período")
            @RequestParam(value = "datas", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Set<LocalDate> datas,
            @Parameter(description = "IDs das métricas que entram no relatório (repita o parâmetro ou separe por vírgula). Sem valor, entram todas")
            @RequestParam(value = "metricaIds", required = false) Set<UUID> metricaIds,
            @Parameter(description = "Blocos que entram no relatório (repita o parâmetro ou separe por vírgula): RESUMO, GRAFICOS, RPE, SESSOES, VALORES. Sem valor, entram todos")
            @RequestParam(value = "conteudos", required = false) Set<ConteudoRelatorio> conteudos
    ) {
        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloAluno(
                usuario, new FiltroRelatorioDTO(modalidadeId, dataInicio, dataFim, datas, metricaIds, conteudos)
        );
        return RelatorioPdfResponse.of(relatorio);
    }

    @GetMapping("/disponibilidade")
    @Operation(
            summary = "Consultar datas e métricas disponíveis para o próprio relatório (US-16)",
            description = "Lista, por modalidade, as datas com registros, as datas com RPE informado e as métricas registradas com suas datas, inclusive as descontinuadas (RN-017). Sem registros, retorna a lista de modalidades vazia e o intervalo nulo."
    )
    @ApiResponse(responseCode = "200", description = "Disponibilidade retornada com sucesso.")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado.")
    @ApiResponse(responseCode = "422", description = "Aluno inativo (RN-014).")
    public ResponseEntity<DisponibilidadeRelatorioDTO> consultarDisponibilidade(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(relatorioPerformanceService.consultarDisponibilidadePeloAluno(usuario));
    }
}
