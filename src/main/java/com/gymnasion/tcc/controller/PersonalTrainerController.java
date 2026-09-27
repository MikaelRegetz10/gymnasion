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
    @Operation(summary = "Recusar solicitação do aluno (US-03)", description = "Remove o registo pendente do aluno.")
    @ApiResponse(responseCode = "204", description = "Aluno recusado com sucesso.")
    public ResponseEntity<Void> recusarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        alunoService.recusarAluno(alunoId, usuario);
        return ResponseEntity.noContent().build();
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
}