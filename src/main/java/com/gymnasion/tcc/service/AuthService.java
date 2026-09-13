package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.LoginRequestDTO;
import com.gymnasion.tcc.dto.TokenResponseDTO;
import com.gymnasion.tcc.infra.security.TokenService;
import com.gymnasion.tcc.repository.UsuarioRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService {

    private final UsuarioRepository userRepository;

    private final TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public TokenResponseDTO login(LoginRequestDTO dto) {
        Usuario user = userRepository.findByEmail(dto.email().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos."));

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }

        String token = tokenService.generateToken(user);
        return TokenResponseDTO.of(token, user.getId(), user.getRole());
    }
}
