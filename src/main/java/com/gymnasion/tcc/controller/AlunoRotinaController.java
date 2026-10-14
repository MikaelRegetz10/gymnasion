package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.PlanejamentoRotinaResponseDTO;
import com.gymnasion.tcc.dto.RegistrarExecucaoSessaoRequestDTO;
import com.gymnasion.tcc.dto.SessaoTreinoResponseDTO;
import com.gymnasion.tcc.service.RotinaTreinoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @PostMapping("/sessoes/{sessaoId}/execucao")
    @Operation(summary = "Registrar execução e confirmar conclusão do treino pelo aluno (US-10 C3, C4, C5 / US-22)", description = "Aluno registra a data real de execução (preservando a planejada), RPE e métricas permitidas, confirmando a conclusão da sessão.")
    @ApiResponse(responseCode = "200", description = "Execução registrada e sessão concluída com sucesso.")
    public ResponseEntity<SessaoTreinoResponseDTO> registrarExecucao(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("sessaoId") UUID sessaoId,
            @Valid @RequestBody RegistrarExecucaoSessaoRequestDTO dto) {
        return ResponseEntity.ok(rotinaTreinoService.registrarExecucao(usuario, sessaoId, dto));
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Consultar histórico de treinos realizados pelo aluno (US-10 C8 / US-21)", description = "Retorna os treinos executados da rotina com resultados.")
    public ResponseEntity<List<SessaoTreinoResponseDTO>> consultarHistoricoRotina(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID rotinaId) {
        return ResponseEntity.ok(rotinaTreinoService.consultarHistoricoRotina(usuario, rotinaId));
    }
}
