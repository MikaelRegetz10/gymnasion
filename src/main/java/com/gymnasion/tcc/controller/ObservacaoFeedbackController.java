package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.ObservacaoFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/observacoes-feedbacks")
@AllArgsConstructor
@PreAuthorize("hasAnyRole('PERSONAL_TRAINER', 'ALUNO')")
@Tag(name = "Observações e Feedbacks (US-26)", description = "Gestão de observações e feedbacks no perfil do aluno (registros imutáveis - US-26 C5)")
@SecurityRequirement(name = "bearerAuth")
public class ObservacaoFeedbackController {

    private final ObservacaoFeedbackService observacaoFeedbackService;

    @PostMapping("/alunos/{alunoId}")
    @Operation(summary = "Registrar observação ou feedback no perfil do aluno (US-26 C2, C3)", description = "Permite ao personal registrar feedback e ao aluno registrar observação. Registros são imutáveis (US-26 C5).")
    @ApiResponse(responseCode = "201", description = "Registro efetuado com sucesso.")
    @ApiResponse(responseCode = "400", description = "Conteúdo obrigatório ou inválido (US-26 C6).")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado.")
    public ResponseEntity<ObservacaoFeedbackResponseDTO> registrar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId,
            @Valid @RequestBody CriarObservacaoFeedbackRequestDTO dto) {
        ObservacaoFeedbackResponseDTO response = observacaoFeedbackService.registrar(usuario, alunoId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/alunos/{alunoId}")
    @Operation(summary = "Consultar histórico de observações e feedbacks do aluno (US-26 C1, C4)", description = "Retorna o histórico de observações do aluno e feedbacks do personal em ordem cronológica decrescente de forma paginada.")
    @ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso.")
    public ResponseEntity<PageResponseDTO<ObservacaoFeedbackResponseDTO>> listar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(observacaoFeedbackService.listarPorAluno(usuario, alunoId, pageable)));
    }
}
