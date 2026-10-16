package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.AlunoResponseDTO;
import com.gymnasion.tcc.service.AlunoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alunos")
@AllArgsConstructor
@PreAuthorize("hasRole('ALUNO')")
@Tag(name = "Aluno - Perfil", description = "Endpoints de consulta e gerenciamento do perfil do próprio aluno")
@SecurityRequirement(name = "bearerAuth")
public class AlunoPerfilController {

    private final AlunoService alunoService;

    @GetMapping("/me")
    @Operation(summary = "Obter dados do perfil do aluno autenticado", description = "Retorna os dados cadastrais do aluno autenticado, modalidade praticada e informações do seu personal trainer vinculado.")
    @ApiResponse(responseCode = "200", description = "Perfil retornado com sucesso.")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado.")
    public ResponseEntity<AlunoResponseDTO> obterMeuPerfil(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.obterMeuPerfil(usuario));
    }
}
