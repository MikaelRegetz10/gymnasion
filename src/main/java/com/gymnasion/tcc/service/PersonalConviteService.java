package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.DuplicateResourceException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.repository.*;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PersonalConviteService {

    private final AlunoRepository alunoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final PersonalConvitesRepository personalConviteRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModalidadeRepository modalidadeRepository;

    @Transactional
    public ConviteResponseDTO gerarLinkConvite(Usuario usuario, GerarConviteRequestDTO dto){
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Personal não encontrado!"));

        Modalidade modalidade = modalidadeRepository.findById(dto.modalidadeId())
                .orElseThrow(() -> new UsernameNotFoundException("Modalidade não encontrada!"));

        String token = UUID.randomUUID().toString();

        PersonalConvites convite = PersonalConvites.builder()
                .token(token)
                .personalId(personalTrainer.getId())
                .status(StatusConvite.ATIVO)
                .modalidade(modalidade)
                .maximoUsuarios(30)
                .quantidadesUsuarios(0)
                .dataExpiracao(LocalDateTime.now().plusMinutes(15))
                .build();

        personalConviteRepository.save(convite);

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String fullUrl = baseUrl + "/invite?token=" + token;

        return new ConviteResponseDTO(fullUrl);
    }

    @Transactional
    public AlunoResponseDTO registroAlunoConvite(UsuarioRequestDTO dto, String token){
        PersonalConvites convite = personalConviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Convite inválido ou não encontrado."));

        PersonalTrainer personal = personalTrainerRepository.getById(convite.getPersonalId());

        String cpfLimpo = dto.cpf().replaceAll("\\D", "");

        if (usuarioRepository.findByEmail(dto.email().toLowerCase().trim()).isPresent()) {
            throw new DuplicateResourceException("Este e-mail já está cadastrado no sistema.");
        }

        if (usuarioRepository.existsByCpf(cpfLimpo)) {
            throw new DuplicateResourceException("Este CPF já está cadastrado no sistema.");
        }

        if (convite.getStatus() != StatusConvite.ATIVO || convite.getDataExpiracao().isBefore(LocalDateTime.now())) {
            throw new BusinessException("O link do convite está expirado ou inativo.");
        }

        if (convite.getQuantidadesUsuarios() >= convite.getMaximoUsuarios()) {
            throw new BusinessException("O limite de utilizações deste link de convite foi atingido.");
        }

        if (usuarioRepository.findByEmail(dto.email()).isPresent()) {
            throw new DuplicateResourceException("Este e-mail já está cadastrado no sistema.");
        }

        Usuario usuario = Usuario.builder()
                .nome(dto.nome())
                .email(dto.email())
                .senhaHash(passwordEncoder.encode(dto.password()))
                .cpf(dto.cpf().replaceAll("\\D", ""))
                .celular(dto.celular().replaceAll("\\D", ""))
                .role(Role.ALUNO)
                .build();

        usuario = usuarioRepository.save(usuario);

        Set<Modalidade> modalidades = new HashSet<>();
        modalidades.add(convite.getModalidade());

        Aluno aluno = Aluno.builder()
                .usuario(usuario)
                .personal(personal)
                .status(StatusConvite.PENDENTE)
                .modalidades(modalidades)
                .build();

        aluno = alunoRepository.save(aluno);

        personalConviteRepository.incrementarQuantidadeUsuarios(convite.getId());

        return toAlunoResponseDTO(aluno);
    }

    @org.springframework.transaction.annotation.Transactional
    public List<AlunoResponseDTO> getAlunosPendentes(Usuario usuario){
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Personal não encontrado!"));

        return alunoRepository.findByPersonalIdAndStatus(personalTrainer.getId(), StatusConvite.PENDENTE)
                .stream()
                .map(this::toAlunoResponseDTO)
                .toList();
    }

    private AlunoResponseDTO toAlunoResponseDTO(Aluno aluno) {
        Set<ModalidadeResponseDTO> modalidadesDTO = aluno.getModalidades().stream()
                .map(m -> new ModalidadeResponseDTO(m.getId(), m.getNome(), m.getDescricao()))
                .collect(Collectors.toSet());

        UsuarioResponseDTO usuarioDTO = new UsuarioResponseDTO(
                aluno.getUsuario().getId(),
                aluno.getUsuario().getNome(),
                aluno.getUsuario().getEmail(),
                aluno.getUsuario().getCpf(),
                aluno.getUsuario().getCelular(),
                aluno.getUsuario().getRole()
        );

        return new AlunoResponseDTO(
                aluno.getId(),
                usuarioDTO,
                aluno.getPersonal().getId(),
                aluno.getPersonal().getUsuario().getNome(),
                aluno.getStatus(),
                modalidadesDTO
        );
    }

    @Transactional
    public AlunoResponseDTO aprovarAluno(UUID alunoId, Usuario usuarioLogado) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new UsernameNotFoundException("Personal Trainer não encontrado!"));

        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        if (!aluno.getPersonal().getId().equals(personalTrainer.getId())) {
            throw new BusinessException("Você não tem permissão para gerenciar este aluno.");
        }

        if (aluno.getStatus() != StatusConvite.PENDENTE) {
            throw new BusinessException("Este aluno não está pendente de aprovação.");
        }

        aluno.setStatus(StatusConvite.ATIVO);
        aluno = alunoRepository.save(aluno);

        return toAlunoResponseDTO(aluno);
    }
}
