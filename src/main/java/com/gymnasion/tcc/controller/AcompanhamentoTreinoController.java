package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.AcompanhamentoAlunoResponseDTO;
import com.gymnasion.tcc.dto.ResumoPendenciasResponseDTO;
import com.gymnasion.tcc.service.AcompanhamentoTreinoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers")
@AllArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Acompanhamento e Pendências (US-24)", description = "Visão geral dos treinamentos, acompanhamento de sessões e pendências de métricas")
@SecurityRequirement(name = "bearerAuth")
public class AcompanhamentoTreinoController {

    private final AcompanhamentoTreinoService acompanhamentoTreinoService;

    @GetMapping("/alunos/{alunoId}/acompanhamento")
    @Operation(
            summary = "Acompanhar estado dos treinamentos do aluno (US-24 C1, C2, C3, C9, C10)",
            description = "Retorna visão geral da rotina ativa do aluno, sessões planejadas e concluídas, indicador e lista detalhada de métricas pendentes do personal."
    )
    @ApiResponse(responseCode = "200", description = "Dados de acompanhamento obtidos com sucesso.")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado ou não pertence a este Personal Trainer.")
    public ResponseEntity<AcompanhamentoAlunoResponseDTO> obterAcompanhamentoAluno(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId) {
        return ResponseEntity.ok(acompanhamentoTreinoService.obterAcompanhamentoAluno(usuario, alunoId));
    }

    @GetMapping("/acompanhamento/pendencias")
    @Operation(
            summary = "Resumo geral de pendências de métricas (US-24 C3)",
            description = "Retorna o total de alunos com pendências e a contagem detalhada de pendências por aluno."
    )
    @ApiResponse(responseCode = "200", description = "Resumo de pendências retornado com sucesso.")
    public ResponseEntity<ResumoPendenciasResponseDTO> obterResumoPendencias(
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(acompanhamentoTreinoService.obterResumoPendencias(usuario));
    }
}
