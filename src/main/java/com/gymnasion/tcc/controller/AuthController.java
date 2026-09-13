package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.AuthService;
import com.gymnasion.tcc.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {

    private final UsuarioService userService;
    private final AuthService authService;

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
}
