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
            description = "Gera o relatório de performance do aluno no período selecionado, com resumo de adesão, sessões (data planejada e data real), métricas tabuladas com gráficos de evolução e esforço percebido (RPE). Métricas descontinuadas permanecem identificadas (RN-017). Sem modalidadeId, o relatório traz uma seção por modalidade com registros no período. Quando o período não possui registros, retorna 422 com a mensagem 'Não há dados para o período selecionado' e o campo periodoSugerido (US-16 C3). As escolhas opcionais datas, metricaIds e conteudos limitam as datas, as métricas e os blocos do PDF; sem elas, o relatório sai completo. Com métricas escolhidas, entram apenas as modalidades que têm alguma delas no período, e o resumo e a lista de sessões continuam considerando todas as sessões dessas modalidades."
    )
    @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso.", content = @Content(mediaType = "application/pdf"))
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado ou não pertence a este Personal Trainer, ou métrica sem registros do aluno.")
    @ApiResponse(responseCode = "422", description = "Período sem dados (com período sugerido), período não informado ou data de início posterior à data de fim.")
    public ResponseEntity<byte[]> gerarRelatorio(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId,
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
        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(
                usuario, alunoId, new FiltroRelatorioDTO(modalidadeId, dataInicio, dataFim, datas, metricaIds, conteudos)
        );
        return RelatorioPdfResponse.of(relatorio);
    }

    @GetMapping("/disponibilidade")
    @Operation(
            summary = "Consultar datas e métricas disponíveis para o relatório do aluno (US-16)",
            description = "Lista, por modalidade, as datas com registros, as datas com RPE informado e as métricas registradas com suas datas, inclusive as descontinuadas (RN-017). Sem registros, retorna a lista de modalidades vazia e o intervalo nulo."
    )
    @ApiResponse(responseCode = "200", description = "Disponibilidade retornada com sucesso.")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado ou não pertence a este Personal Trainer.")
    public ResponseEntity<DisponibilidadeRelatorioDTO> consultarDisponibilidade(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId
    ) {
        return ResponseEntity.ok(relatorioPerformanceService.consultarDisponibilidadePeloPersonal(usuario, alunoId));
    }
}
