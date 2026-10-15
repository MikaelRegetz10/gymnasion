package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.AlunoService;
import com.gymnasion.tcc.service.PersonalConviteService;
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
import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers")
@AllArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Gestão de Alunos", description = "Gestão de convites, aprovações, desativações e listagem de atletas")
@SecurityRequirement(name = "bearerAuth")
public class PersonalTrainerController {

    private final PersonalConviteService conviteService;
    private final AlunoService alunoService;
    private final com.gymnasion.tcc.service.ObservacaoFeedbackService observacaoFeedbackService;

    @PostMapping("/convites")
    @Operation(summary = "Gerar link de convite temporário (US-03)", description = "Gera um link de registo válido por 15 minutos para partilhar com o aluno.")
    @ApiResponse(responseCode = "201", description = "Link gerado com sucesso.")
    public ResponseEntity<ConviteResponseDTO> gerarLinkConvite(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody GerarConviteRequestDTO dto) {
        ConviteResponseDTO convite = conviteService.gerarLinkConvite(usuario, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(convite);
    }

    @GetMapping("/alunos")
    @Operation(summary = "Listar todos os alunos vinculados (US-03)", description = "Retorna os alunos vinculados ao Personal Trainer autenticado.")
    public ResponseEntity<List<AlunoResponseDTO>> getAlunos(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.getAlunosVinculados(usuario));
    }

    @GetMapping("/alunos/pendentes")
    @Operation(summary = "Listar alunos com aprovação pendente (US-03)", description = "Retorna as solicitações de alunos que aguardam aprovação.")
    public ResponseEntity<List<AlunoResponseDTO>> getAlunosPendentes(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.getAlunosPendentes(usuario));
    }

    @GetMapping("/alunos/inativos")
    @Operation(summary = "Listar alunos inativos (US-05)", description = "Retorna os alunos desativados temporariamente.")
    public ResponseEntity<List<AlunoResponseDTO>> getAlunosInativos(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.getAlunosInativos(usuario));
    }

    @PatchMapping("/alunos/{alunoId}/aprovar")
    @Operation(summary = "Aprovar solicitação do aluno (US-03)", description = "Altera o estado do aluno para ATIVO e aprova a ligação.")
    public ResponseEntity<AlunoResponseDTO> aprovarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.aprovarAluno(alunoId, usuario));
    }

    @PatchMapping("/alunos/{alunoId}/recusar")
    @Operation(summary = "Recusar solicitação do aluno (US-03)", description = "Remove o registo pendente do aluno e envia e-mail automático informando a recusa.")
    @ApiResponse(responseCode = "200", description = "Aluno recusado com sucesso.")
    public ResponseEntity<MensagemResponseDTO> recusarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.recusarAluno(alunoId, usuario));
    }

    @PatchMapping("/alunos/{alunoId}/desativar")
    @Operation(summary = "Desativar perfil do aluno (US-05)", description = "Suspende o acesso do aluno mantendo o seu histórico intacto (RN-014).")
    public ResponseEntity<MensagemResponseDTO> desativarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.desativarAluno(alunoId, usuario));
    }

    @PatchMapping("/alunos/{alunoId}/reativar")
    @Operation(summary = "Reativar aluno (US-05)", description = "Restabelece o acesso do aluno ao sistema.")
    public ResponseEntity<MensagemResponseDTO> reativarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.reativarAluno(alunoId, usuario));
    }

    @PostMapping("/alunos/{alunoId}/observacoes-feedbacks")
    @Operation(summary = "Registrar feedback sobre o aluno (US-26 C2)", description = "Permite ao personal responsável registrar um feedback imutável no perfil do aluno.")
    @ApiResponse(responseCode = "201", description = "Feedback registrado com sucesso.")
    @ApiResponse(responseCode = "400", description = "Conteúdo obrigatório ou inválido (US-26 C6).")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado ou não pertence a este personal.")
    public ResponseEntity<ObservacaoFeedbackResponseDTO> registrarFeedback(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId,
            @Valid @RequestBody CriarObservacaoFeedbackRequestDTO dto) {
        ObservacaoFeedbackResponseDTO response = observacaoFeedbackService.registrarFeedbackPeloPersonal(usuario, alunoId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/alunos/{alunoId}/observacoes-feedbacks")
    @Operation(summary = "Consultar histórico de observações e feedbacks do aluno (US-26 C1, C4)", description = "Retorna o histórico de observações do aluno e feedbacks do personal em ordem cronológica decrescente.")
    @ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso.")
    public ResponseEntity<List<ObservacaoFeedbackResponseDTO>> listarObservacoesEFeedbacks(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId) {
        return ResponseEntity.ok(observacaoFeedbackService.listarObservacoesEFeedbacksPeloPersonal(usuario, alunoId));
    }
}