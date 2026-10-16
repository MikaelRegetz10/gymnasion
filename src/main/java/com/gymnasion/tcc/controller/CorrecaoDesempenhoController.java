package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.AuditoriaDesempenhoResponseDTO;
import com.gymnasion.tcc.dto.CorrigirDesempenhoRequestDTO;
import com.gymnasion.tcc.dto.DesempenhoDetalheResponseDTO;
import com.gymnasion.tcc.dto.PageResponseDTO;
import com.gymnasion.tcc.service.CorrecaoDesempenhoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers/rotinas/sessoes/{sessaoId}/desempenho")
@AllArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Correção de Desempenho", description = "Correção de registros de desempenho, preenchimento de métricas pendentes e auditoria das alterações (US-23)")
@SecurityRequirement(name = "bearerAuth")
public class CorrecaoDesempenhoController {

    private final CorrecaoDesempenhoService correcaoDesempenhoService;

    @GetMapping
    @Operation(summary = "Acessar registro de desempenho para correção (US-23 C1)", description = "Retorna o registro de desempenho da sessão com o valor atual de cada métrica e indica quais valores podem ser corrigidos e quais métricas pendentes podem ser preenchidas.")
    @ApiResponse(responseCode = "200", description = "Registro de desempenho retornado com sucesso.")
    @ApiResponse(responseCode = "404", description = "Sessão não encontrada para o personal responsável (US-23 C8).")
    @ApiResponse(responseCode = "422", description = "A sessão ainda não possui registro de desempenho.")
    public ResponseEntity<DesempenhoDetalheResponseDTO> consultarRegistro(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("sessaoId") UUID sessaoId) {
        return ResponseEntity.ok(correcaoDesempenhoService.consultarRegistro(usuario, sessaoId));
    }

    @PatchMapping
    @Operation(summary = "Corrigir registro de desempenho (US-23 C2 a C7)", description = "Corrige valores de métricas, a data real de execução (preservando a planejada) e as observações, e preenche métricas pendentes do personal. Todas as alterações são validadas antes de salvar e registradas na auditoria como inclusão ou correção.")
    @ApiResponse(responseCode = "200", description = "Correção salva e auditada com sucesso.")
    @ApiResponse(responseCode = "404", description = "Sessão não encontrada para o personal responsável (US-23 C8).")
    @ApiResponse(responseCode = "422", description = "Valores inválidos (campos identificados em invalidFields), métrica exclusiva do aluno ou descontinuada sem valor, sessão sem registro ou nenhuma alteração informada.")
    public ResponseEntity<DesempenhoDetalheResponseDTO> corrigirRegistro(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("sessaoId") UUID sessaoId,
            @Valid @RequestBody CorrigirDesempenhoRequestDTO dto) {
        return ResponseEntity.ok(correcaoDesempenhoService.corrigirRegistro(usuario, sessaoId, dto));
    }

    @GetMapping("/auditoria")
    @Operation(summary = "Consultar histórico de alterações do desempenho (US-23 C5)", description = "Retorna as inclusões e correções do registro, com valor anterior, novo valor, responsável e data/hora, em ordem cronológica de forma paginada.")
    @ApiResponse(responseCode = "200", description = "Histórico de alterações retornado com sucesso.")
    @ApiResponse(responseCode = "404", description = "Sessão não encontrada para o personal responsável (US-23 C8).")
    public ResponseEntity<PageResponseDTO<AuditoriaDesempenhoResponseDTO>> consultarAuditoria(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("sessaoId") UUID sessaoId,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(correcaoDesempenhoService.consultarAuditoria(usuario, sessaoId, pageable)));
    }
}
