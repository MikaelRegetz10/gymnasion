package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Modalidade;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.PreferenciaUsuario;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.DuplicateResourceException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final ModalidadeRepository modalidadeRepository;
    private final AlunoRepository alunoRepository;
    private final PreferenciaUsuarioRepository preferenciaUsuarioRepository;

    @Transactional
    public UsuarioResponseDTO criarPersonal(RegistroPersonalCompletoDTO dto) {
        String email = dto.email().toLowerCase().trim();
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Já possui um usuário vinculado a esse e-mail.");
        }

        Modalidade modalidade = modalidadeRepository.findById(dto.modalidade())
                .orElseThrow(() -> new NotFoundException("Modalidade não encontrada."));

        String cpfLimpo = dto.cpf().replaceAll("\\D", "");

        if (usuarioRepository.existsByCpf(cpfLimpo)) {
            throw new DuplicateResourceException("Este CPF já está cadastrado no sistema.");
        }

        Usuario usuario = Usuario.builder()
                .nome(dto.nome().trim())
                .email(email)
                .senhaHash(passwordEncoder.encode(dto.password()))
                .cpf(cpfLimpo)
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

    @Transactional(readOnly = true)
    public UsuarioPerfilResponseDTO obterMeuPerfil(Usuario usuarioAuth) {
        Usuario usuario = usuarioRepository.findById(usuarioAuth.getId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        String modalidadePrincipal = null;
        Long modalidadePrincipalId = null;
        List<String> modalidadesAluno = null;

        if (usuario.getRole() == Role.PERSONAL_TRAINER) {
            var personalOpt = personalTrainerRepository.getByUsuario(usuario);
            if (personalOpt.isPresent() && personalOpt.get().getModalidade() != null) {
                modalidadePrincipal = personalOpt.get().getModalidade().getNome();
                modalidadePrincipalId = personalOpt.get().getModalidade().getId();
            }
        } else if (usuario.getRole() == Role.ALUNO) {
            var alunoOpt = alunoRepository.findByUsuario(usuario);
            if (alunoOpt.isPresent() && alunoOpt.get().getModalidades() != null) {
                modalidadesAluno = alunoOpt.get().getModalidades().stream()
                        .map(Modalidade::getNome)
                        .toList();
            }
        }

        return new UsuarioPerfilResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getCpf(),
                usuario.getCelular(),
                usuario.getRole(),
                modalidadePrincipal,
                modalidadePrincipalId,
                modalidadesAluno != null ? modalidadesAluno : List.of(),
                usuario.getDataCriacao()
        );
    }

    @Transactional
    public UsuarioPerfilResponseDTO atualizarDadosCadastrais(Usuario usuarioAuth, AtualizarUsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(usuarioAuth.getId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        String nomeLimpo = dto.nome().trim();
        String celularLimpo = dto.celular().replaceAll("\\D", "");

        if (celularLimpo.length() < 10 || celularLimpo.length() > 13) {
            throw new BusinessException("Informe um número de celular válido com DDD.");
        }

        usuario.setNome(nomeLimpo);
        usuario.setCelular(celularLimpo);
        usuarioRepository.save(usuario);

        return obterMeuPerfil(usuario);
    }

    @Transactional(readOnly = true)
    public PreferenciaUsuarioResponseDTO obterPreferencias(Usuario usuarioAuth) {
        PreferenciaUsuario pref = preferenciaUsuarioRepository.findByUsuarioId(usuarioAuth.getId())
                .orElse(null);

        if (pref == null) {
            return new PreferenciaUsuarioResponseDTO(true, false, false);
        }
        return new PreferenciaUsuarioResponseDTO(
                Boolean.TRUE.equals(pref.getShowNotificationBadge()),
                Boolean.TRUE.equals(pref.getNotificacoesEmail()),
                Boolean.TRUE.equals(pref.getNotificacoesPush())
        );
    }

    @Transactional
    public PreferenciaUsuarioResponseDTO atualizarPreferencias(Usuario usuarioAuth, PreferenciaUsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(usuarioAuth.getId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        PreferenciaUsuario pref = preferenciaUsuarioRepository.findByUsuarioId(usuario.getId())
                .orElseGet(() -> PreferenciaUsuario.builder().usuario(usuario).build());

        if (dto.showNotificationBadge() != null) {
            pref.setShowNotificationBadge(dto.showNotificationBadge());
        }
        if (dto.notificacoesEmail() != null) {
            pref.setNotificacoesEmail(dto.notificacoesEmail());
        }
        if (dto.notificacoesPush() != null) {
            pref.setNotificacoesPush(dto.notificacoesPush());
        }
        pref.setDataAtualizacao(OffsetDateTime.now());

        pref = preferenciaUsuarioRepository.save(pref);

        return new PreferenciaUsuarioResponseDTO(
                Boolean.TRUE.equals(pref.getShowNotificationBadge()),
                Boolean.TRUE.equals(pref.getNotificacoesEmail()),
                Boolean.TRUE.equals(pref.getNotificacoesPush())
        );
    }

    @Transactional
    public MensagemResponseDTO alterarSenha(Usuario usuarioAuth, AlterarSenhaRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(usuarioAuth.getId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenhaHash())) {
            throw new BusinessException("A senha atual informada está incorreta.");
        }

        if (!dto.novaSenha().equals(dto.confirmacaoNovaSenha())) {
            throw new BusinessException("A confirmação da nova senha não coincide com a nova senha.");
        }

        if (dto.senhaAtual().equals(dto.novaSenha())) {
            throw new BusinessException("A nova senha deve ser diferente da senha atual.");
        }

        validarPoliticaSenhaForte(dto.novaSenha());

        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha()));
        usuarioRepository.save(usuario);

        return new MensagemResponseDTO("Senha alterada com sucesso!");
    }

    public void validarPoliticaSenhaForte(String senha) {
        if (senha == null || senha.length() < 8
                || !Pattern.compile("[A-Z]").matcher(senha).find()
                || !Pattern.compile("[a-z]").matcher(senha).find()
                || !Pattern.compile("[0-9]").matcher(senha).find()
                || !Pattern.compile("[!@#$%¨&*^~_+=/\\\\|?.,:;<>{}()\\[\\]-]").matcher(senha).find()) {
            throw new BusinessException("A senha deve conter no mínimo 8 caracteres, incluindo letra maiúscula, minúscula, número e caractere especial.");
        }
    }
}
