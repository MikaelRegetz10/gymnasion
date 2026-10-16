package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.RotinaTreinoService;
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
@RequestMapping("/alunos/rotinas")
@AllArgsConstructor
@PreAuthorize("hasRole('ALUNO')")
@Tag(name = "Aluno - Rotinas e Sessões de Treino", description = "Consulta de planejamento, registro de execução e histórico de treinos pelo aluno (US-10 / US-21 / US-22)")
@SecurityRequirement(name = "bearerAuth")
public class AlunoRotinaController {

    private final RotinaTreinoService rotinaTreinoService;

    @GetMapping("/{id}/planejamento")
    @Operation(summary = "Consultar planejamento da rotina pelo aluno (US-10 C1 / US-21)", description = "Permite ao aluno visualizar o planejamento de treinos por semanas.")
    public ResponseEntity<PlanejamentoRotinaResponseDTO> consultarPlanejamento(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID rotinaId) {
        return ResponseEntity.ok(rotinaTreinoService.consultarPlanejamento(usuario, rotinaId));
    }

    @GetMapping("/sessoes/{sessaoId}/execucao")
    @Operation(summary = "Acessar o registro de execução da sessão (US-22 C1)", description = "Retorna os dados da sessão e as métricas configuradas pelo personal, com tipo, obrigatoriedade e responsável pelo preenchimento, para montar o formulário de registro.")
    @ApiResponse(responseCode = "200", description = "Formulário de registro retornado com sucesso.")
    public ResponseEntity<FormularioExecucaoResponseDTO> obterFormularioExecucao(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("sessaoId") UUID sessaoId) {
        return ResponseEntity.ok(rotinaTreinoService.obterFormularioExecucao(usuario, sessaoId));
    }

    @PostMapping("/sessoes/{sessaoId}/execucao")
    @Operation(summary = "Registrar execução e confirmar conclusão do treino pelo aluno (US-10 / US-13 / US-22 / RN-015)", description = "Aluno registra a data real de execução (preservando a planejada), RPE e métricas permitidas, confirmando a conclusão da sessão. Valida os tipos de dados conforme RN-015 (US-13 C2 / US-22 C9). Métricas obrigatórias do aluno devem ser informadas; opcionais podem ser omitidas e as exclusivas do personal ficam pendentes. Após a confirmação, o registro não pode ser alterado pelo aluno.")
    @ApiResponse(responseCode = "200", description = "Execução registrada e sessão concluída com sucesso.")
    @ApiResponse(responseCode = "422", description = "Sessão já confirmada (correções via US-23), métricas obrigatórias não preenchidas ou valores inválidos para o tipo configurado (RN-015 / US-13 C2 / US-22 C9).")
    public ResponseEntity<SessaoTreinoResponseDTO> registrarExecucao(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("sessaoId") UUID sessaoId,
            @Valid @RequestBody RegistrarExecucaoSessaoRequestDTO dto) {
        return ResponseEntity.ok(rotinaTreinoService.registrarExecucao(usuario, sessaoId, dto));
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Consultar histórico de treinos realizados pelo aluno (US-10 C8 / US-21)", description = "Retorna os treinos executados da rotina com resultados de forma paginada.")
    public ResponseEntity<PageResponseDTO<SessaoTreinoResponseDTO>> consultarHistoricoRotina(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID rotinaId,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(rotinaTreinoService.consultarHistoricoRotina(usuario, rotinaId, pageable)));
    }
}
