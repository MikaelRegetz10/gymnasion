package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.AuthService;
import com.gymnasion.tcc.service.PersonalConviteService;
import com.gymnasion.tcc.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {

    private final UsuarioService userService;
    private final AuthService authService;
    private final PersonalConviteService conviteService;

    @PostMapping("/registro-personal")
    public ResponseEntity<UsuarioResponseDTO> registrarPersonal(@Valid @RequestBody RegistroPersonalCompletoDTO dto) {
        UsuarioResponseDTO response = userService.criarPersonal(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        TokenResponseDTO response = authService.login(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/registro-aluno-convite")
    public ResponseEntity<AlunoResponseDTO> registrarAlunoConvite(
            @RequestParam("token") String token,
            @Valid @RequestBody UsuarioRequestDTO dto) {
        AlunoResponseDTO response = conviteService.registroAlunoConvite(dto, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
