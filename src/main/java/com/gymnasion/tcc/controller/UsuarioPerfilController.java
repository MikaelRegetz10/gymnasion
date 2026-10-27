package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.UsuarioService;
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

@RestController
@RequestMapping("/usuarios")
@AllArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Usuários - Perfil e Configurações", description = "Endpoints de consulta e atualização de perfil, dados cadastrais, preferências de notificação e alteração de senha")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioPerfilController {

    private final UsuarioService usuarioService;

    @GetMapping("/me")
    @Operation(summary = "Obter dados cadastrais do usuário autenticado", description = "Retorna nome, e-mail, CPF, celular, papel e modalidades vinculadas.")
    @ApiResponse(responseCode = "200", description = "Dados do perfil retornados com sucesso.")
    public ResponseEntity<UsuarioPerfilResponseDTO> obterMeuPerfil(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(usuarioService.obterMeuPerfil(usuario));
    }

    @PutMapping("/me")
    @Operation(summary = "Atualizar dados cadastrais do usuário autenticado", description = "Permite alterar nome e celular com validação de formato.")
    @ApiResponse(responseCode = "200", description = "Dados cadastrais atualizados com sucesso.")
    @ApiResponse(responseCode = "400", description = "Dados inválidos.")
    public ResponseEntity<UsuarioPerfilResponseDTO> atualizarDadosCadastrais(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody AtualizarUsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizarDadosCadastrais(usuario, dto));
    }

    @GetMapping("/me/preferencias")
    @Operation(summary = "Obter preferências de notificações", description = "Retorna configurações de badge no sino, e-mail e push.")
    @ApiResponse(responseCode = "200", description = "Preferências retornadas com sucesso.")
    public ResponseEntity<PreferenciaUsuarioResponseDTO> obterPreferencias(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(usuarioService.obterPreferencias(usuario));
    }

    @PutMapping("/me/preferencias")
    @Operation(summary = "Atualizar preferências de notificações", description = "Salva preferências de badge no sino, e-mail e push.")
    @ApiResponse(responseCode = "200", description = "Preferências atualizadas com sucesso.")
    public ResponseEntity<PreferenciaUsuarioResponseDTO> atualizarPreferencias(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody PreferenciaUsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizarPreferencias(usuario, dto));
    }

    @PatchMapping("/me/senha")
    @Operation(summary = "Alterar senha do usuário autenticado (RN-002)", description = "Valida senha atual e nova senha com política de senha forte.")
    @ApiResponse(responseCode = "200", description = "Senha alterada com sucesso.")
    @ApiResponse(responseCode = "422", description = "Senha atual incorreta, confirmação divergente ou senha fraca.")
    public ResponseEntity<MensagemResponseDTO> alterarSenha(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody AlterarSenhaRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.alterarSenha(usuario, dto));
    }
}
