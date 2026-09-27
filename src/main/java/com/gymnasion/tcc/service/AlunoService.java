package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.AlunoResponseDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.dto.ModalidadeResponseDTO;
import com.gymnasion.tcc.dto.UsuarioResponseDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AlunoService {

    private final AlunoRepository alunoRepository;
    private final PersonalTrainerRepository personalTrainerRepository;

    @Transactional(readOnly = true)
    public List<AlunoResponseDTO> getAlunosVinculados(Usuario usuario) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Personal não encontrado!"));

        return alunoRepository.findByPersonalId(personalTrainer.getId())
                .stream()
                .map(this::toAlunoResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlunoResponseDTO> getAlunosInativos(Usuario usuarioLogado) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new UsernameNotFoundException("Personal Trainer não encontrado!"));

        return alunoRepository.findByPersonalIdAndStatus(personal.getId(), StatusConvite.INATIVO)
                .stream()
                .map(this::toAlunoResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlunoResponseDTO> getAlunosPendentes(Usuario usuario){
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Personal não encontrado!"));

        return alunoRepository.findByPersonalIdAndStatus(personalTrainer.getId(), StatusConvite.PENDENTE)
                .stream()
                .map(this::toAlunoResponseDTO)
                .toList();
    }

    @Transactional
    public MensagemResponseDTO desativarAluno(UUID alunoId, Usuario usuarioLogado) {
        Aluno aluno = validarEPegarAlunoDoPersonal(alunoId, usuarioLogado);

        aluno.setStatus(StatusConvite.INATIVO);
        alunoRepository.save(aluno);

        return new MensagemResponseDTO("Aluno desativado com sucesso");
    }

    @Transactional
    public MensagemResponseDTO reativarAluno(UUID alunoId, Usuario usuarioLogado) {
        Aluno aluno = validarEPegarAlunoDoPersonal(alunoId, usuarioLogado);

        aluno.setStatus(StatusConvite.ATIVO);
        alunoRepository.save(aluno);

        return new MensagemResponseDTO("Aluno reativado com sucesso!");
    }

    @Transactional
    public AlunoResponseDTO aprovarAluno(UUID alunoId, Usuario usuarioLogado) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado!"));

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

    @Transactional
    public void recusarAluno(UUID alunoId, Usuario usuarioLogado) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado!"));

        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        if (!aluno.getPersonal().getId().equals(personalTrainer.getId())) {
            throw new BusinessException("Você não tem permissão para gerenciar este aluno.");
        }

        if (aluno.getStatus() != StatusConvite.PENDENTE) {
            throw new BusinessException("Este aluno não está pendente de aprovação.");
        }

        alunoRepository.delete(aluno);
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

    private Aluno validarEPegarAlunoDoPersonal(UUID alunoId, Usuario usuarioLogado) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new UsernameNotFoundException("Personal Trainer não encontrado!"));

        return alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));
    }

}
