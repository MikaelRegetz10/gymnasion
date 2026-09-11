package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.dto.UsuarioRequestDTO;
import com.gymnasion.tcc.dto.UsuarioResponseDTO;
import com.gymnasion.tcc.repository.UsuarioRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponseDTO criarPersonal(UsuarioRequestDTO dto) {
        String email = dto.email().toLowerCase().trim();
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("E-mail já cadastrado.");
        }

        Usuario usuario = Usuario.builder()
                .nome(dto.nome().trim())
                .email(email)
                .senhaHash(passwordEncoder.encode(dto.password()))
                .cpf(dto.cpf().replaceAll("\\D", ""))
                .celular(dto.celular().replaceAll("\\D", ""))
                .role(Role.PERSONAL_TRAINER)
                .build();

        usuario = usuarioRepository.save(usuario);

        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getCpf(),
                usuario.getCelular(),
                usuario.getRole()
        );
    }
}
