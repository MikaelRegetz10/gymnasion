package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.CriarObservacaoFeedbackRequestDTO;
import com.gymnasion.tcc.dto.ObservacaoFeedbackResponseDTO;
import com.gymnasion.tcc.service.ObservacaoFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alunos/observacoes-feedbacks")
@AllArgsConstructor
@PreAuthorize("hasRole('ALUNO')")
@Tag(name = "Aluno - Observações e Feedbacks (US-26)", description = "Registro de observações sobre experiências/dificuldades e consulta do histórico pelo aluno")
@SecurityRequirement(name = "bearerAuth")
public class AlunoObservacaoFeedbackController {

    private final ObservacaoFeedbackService observacaoFeedbackService;

    @PostMapping
    @Operation(summary = "Registrar observação no perfil (US-26 C3)", description = "Permite ao aluno registrar uma observação sobre suas experiências ou dificuldades no seu perfil. Registros são imutáveis (US-26 C5).")
    @ApiResponse(responseCode = "201", description = "Observação registrada com sucesso.")
    @ApiResponse(responseCode = "400", description = "Conteúdo obrigatório ou inválido (US-26 C6).")
    @ApiResponse(responseCode = "422", description = "Aluno inativo (RN-014).")
    public ResponseEntity<ObservacaoFeedbackResponseDTO> registrarObservacao(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody CriarObservacaoFeedbackRequestDTO dto) {
        ObservacaoFeedbackResponseDTO response = observacaoFeedbackService.registrarObservacaoPeloAluno(usuario, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Consultar histórico de observações e feedbacks (US-26 C1, C4)", description = "Retorna o histórico de observações do próprio aluno e feedbacks do seu personal trainer em ordem cronológica decrescente.")
    @ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso.")
    public ResponseEntity<List<ObservacaoFeedbackResponseDTO>> listarObservacoesEFeedbacks(
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(observacaoFeedbackService.listarObservacoesEFeedbacksPeloAluno(usuario));
    }
}
