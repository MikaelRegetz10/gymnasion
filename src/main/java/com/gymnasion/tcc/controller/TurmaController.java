package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.TurmaService;
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
@RequestMapping("/personal-trainers/turmas")
@AllArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Gestão de Turmas", description = "Criação, visualização e gerenciamento de turmas de alunos")
@SecurityRequirement(name = "bearerAuth")
public class TurmaController {

    private final TurmaService turmaService;

    @PostMapping
    @Operation(summary = "Criar turma com sucesso (US-07)", description = "Cria uma nova turma vinculando modalidade e alunos.")
    @ApiResponse(responseCode = "201", description = "Turma criada com sucesso!")
    @ApiResponse(responseCode = "422", description = "Precisa ter pelo menos um aluno vinculado para conseguir criar uma turma.")
    public ResponseEntity<TurmaResponseDTO> criarTurma(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody CriarTurmaRequestDTO dto) {
        TurmaResponseDTO response = turmaService.criarTurma(usuario, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Visualizar turmas (US-08)", description = "Retorna a lista de todas as turmas criadas pelo Personal Trainer.")
    public ResponseEntity<List<TurmaResponseDTO>> listarTurmas(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(turmaService.listarTurmas(usuario));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar turma por ID (US-08)", description = "Retorna os detalhes completos de uma turma específica.")
    public ResponseEntity<TurmaResponseDTO> buscarPorId(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(turmaService.buscarPorId(usuario, id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar dados da turma (US-09 - Cenário 1)", description = "Atualiza nome e modalidade da turma.")
    public ResponseEntity<TurmaResponseDTO> atualizarTurma(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody AtualizarTurmaRequestDTO dto) {
        return ResponseEntity.ok(turmaService.atualizarTurma(usuario, id, dto));
    }

    @PostMapping("/{id}/alunos/{alunoId}")
    @Operation(summary = "Adicionar aluno à turma (US-09 - Cenário 3)", description = "Vincular um aluno existente à turma.")
    public ResponseEntity<MensagemResponseDTO> adicionarAluno(
            @PathVariable("id") UUID id,
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(turmaService.adicionarAluno(usuario, id, alunoId));
    }

    @DeleteMapping("/{id}/alunos/{alunoId}")
    @Operation(summary = "Remover aluno da turma (US-09 - Cenários 5 e 6)", description = "Remove o aluno da turma. Se a turma possuir apenas 1 aluno, ela é apagada automaticamente.")
    public ResponseEntity<MensagemResponseDTO> removerAluno(
            @PathVariable("id") UUID id,
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(turmaService.removerAluno(usuario, id, alunoId));
    }
}