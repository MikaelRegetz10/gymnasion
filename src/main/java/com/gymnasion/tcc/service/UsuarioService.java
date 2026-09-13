package com.gymnasion.tcc.service;

import com.gymnasion.tcc.NotFoundException;
import com.gymnasion.tcc.domain.Modalidade;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.dto.RegistroPersonalCompletoDTO;
import com.gymnasion.tcc.dto.UsuarioRequestDTO;
import com.gymnasion.tcc.dto.UsuarioResponseDTO;
import com.gymnasion.tcc.repository.ModalidadeRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
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
    private final PersonalTrainerRepository personalTrainerRepository;
    private final ModalidadeRepository modalidadeRepository;

    @Transactional
    public UsuarioResponseDTO criarPersonal(RegistroPersonalCompletoDTO dto) {
        String email = dto.email().toLowerCase().trim();
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Já possui um usuário vinculado a esse e-mail.");
        }

        Modalidade modalidade = modalidadeRepository.findById(dto.modalidade())
                .orElseThrow(() -> new NotFoundException("Modalidade não encontrada."));

        Usuario usuario = Usuario.builder()
                .nome(dto.nome().trim())
                .email(email)
                .senhaHash(passwordEncoder.encode(dto.password()))
                .cpf(dto.cpf().replaceAll("\\D", ""))
                .celular(dto.celular().replaceAll("\\D", ""))
                .role(Role.PERSONAL_TRAINER)
                .build();

        usuario = usuarioRepository.save(usuario);

        PersonalTrainer personalTrainer = PersonalTrainer.builder()
                .usuario(usuario)
                .modalidade(modalidade)
                .build();

        personalTrainerRepository.save(personalTrainer);

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
