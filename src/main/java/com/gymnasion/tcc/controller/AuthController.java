package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.AuthService;
import com.gymnasion.tcc.service.PersonalConviteService;
import com.gymnasion.tcc.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
@Tag(name = "Autenticação & Registos", description = "Endpoints de login, registo de treinador e auto-registo de aluno via convite")
public class AuthController {

    private final UsuarioService userService;
    private final AuthService authService;
    private final PersonalConviteService conviteService;

    @PostMapping("/registro-personal")
    @Operation(summary = "Registar novo Personal Trainer (US-01)", description = "Cria a conta do treinador e associa a sua modalidade principal.")
    @ApiResponse(responseCode = "201", description = "Treinador registado com sucesso.")
    @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.")
    @ApiResponse(responseCode = "409", description = "E-mail ou CPF já registado na plataforma.")
    public ResponseEntity<UsuarioResponseDTO> registrarPersonal(@Valid @RequestBody RegistroPersonalCompletoDTO dto) {
        UsuarioResponseDTO response = userService.criarPersonal(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar utilizador (US-02)", description = "Realiza a autenticação e retorna o token JWT de acesso. Implementa bloqueio temporário após 5 tentativas inválidas por 5 minutos (RN-010) e restrição de acesso a alunos inativos (RN-014).")
    @ApiResponse(responseCode = "200", description = "Autenticação realizada com sucesso.")
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos (US-02 C3).")
    @ApiResponse(responseCode = "422", description = "Conta temporariamente bloqueada após 5 tentativas inválidas (RN-010) ou aluno inativo (RN-014).")
    public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        TokenResponseDTO response = authService.login(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/registro-aluno-convite")
    @Operation(summary = "Registar aluno via link de convite (US-04)", description = "Permite o auto-registo do aluno através do token gerado pelo Personal Trainer.")
    @ApiResponse(responseCode = "201", description = "Registo efetuado com sucesso, a aguardar aprovação.")
    @ApiResponse(responseCode = "400", description = "Convite expirado, inativo ou limite de utilizações atingido.")
    public ResponseEntity<AlunoResponseDTO> registrarAlunoConvite(
            @RequestParam("token") String token,
            @Valid @RequestBody UsuarioRequestDTO dto) {
        AlunoResponseDTO response = conviteService.registroAlunoConvite(dto, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}