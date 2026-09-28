package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.CriarRotinaTreinoRequestDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.dto.RotinaTreinoResponseDTO;
import com.gymnasion.tcc.service.RotinaTreinoService;
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
@RequestMapping("/personal-trainers/rotinas")
@AllArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Rotinas de Treino", description = "Criação, consulta, vinculação e exclusão de rotinas de treino")
@SecurityRequirement(name = "bearerAuth")
public class RotinaTreinoController {

    private final RotinaTreinoService rotinaTreinoService;

    @PostMapping
    @Operation(summary = "Cadastrar rotina de treino (US-10)", description = "Cria uma nova rotina de treino vinculando modalidade, métricas personalizadas e alunos.")
    @ApiResponse(responseCode = "201", description = "Treino criado com sucesso!")
    @ApiResponse(responseCode = "400", description = "Campos obrigatórios ausentes, frequência inválida ou sem métricas cadastradas.")
    public ResponseEntity<RotinaTreinoResponseDTO> criarRotina(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody CriarRotinaTreinoRequestDTO dto) {
        RotinaTreinoResponseDTO response = rotinaTreinoService.criarRotinaTreino(usuario, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar todas as rotinas do Personal Trainer (US-10)", description = "Retorna todas as rotinas de treino criadas pelo Personal Trainer autenticado.")
    public ResponseEntity<List<RotinaTreinoResponseDTO>> listarMinhasRotinas(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(rotinaTreinoService.listarPorPersonal(usuario));
    }

    @GetMapping("/aluno/{alunoId}")
    @Operation(summary = "Visualizar rotinas de treino do aluno (US-11)", description = "Retorna as rotinas associadas a um aluno específico.")
    public ResponseEntity<List<RotinaTreinoResponseDTO>> listarPorAluno(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId) {
        return ResponseEntity.ok(rotinaTreinoService.listarPorAluno(usuario, alunoId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar rotina de treino por ID (US-10)", description = "Retorna os detalhes completos de uma rotina de treino.")
    public ResponseEntity<RotinaTreinoResponseDTO> buscarPorId(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(rotinaTreinoService.buscarPorId(usuario, id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir rotina de treino (US-12)", description = "Exclui uma rotina de treino do Personal Trainer.")
    @ApiResponse(responseCode = "200", description = "Rotina excluída com sucesso!")
    public ResponseEntity<MensagemResponseDTO> excluirRotina(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(rotinaTreinoService.excluirRotina(usuario, id));
    }
}