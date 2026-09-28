package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.ModalidadeRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import com.gymnasion.tcc.repository.TurmaRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class TurmaService {

    private final TurmaRepository turmaRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final ModalidadeRepository modalidadeRepository;
    private final AlunoRepository alunoRepository;

    @Transactional
    public TurmaResponseDTO criarTurma(Usuario usuario, CriarTurmaRequestDTO dto) {
        PersonalTrainer personal = obterPersonalLogado(usuario);

        List<Aluno> alunosDoPersonal = alunoRepository.findByPersonalId(personal.getId());
        if (alunosDoPersonal.isEmpty()) {
            throw new BusinessException("Precisa ter pelo menos um aluno vinculado para conseguir criar uma turma");
        }

        Modalidade modalidade = modalidadeRepository.findById(dto.modalidadeId())
                .orElseThrow(() -> new NotFoundException("Modalidade não encontrada."));

        Set<Aluno> alunosValidados = new HashSet<>();
        for (UUID alunoId : dto.alunosIds()) {
            Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

            if (aluno.getStatus() == StatusConvite.INATIVO) {
                throw new BusinessException("Erro. Este aluno está inativo.");
            }
            alunosValidados.add(aluno);
        }

        Turma turma = Turma.builder()
                .nome(dto.nome().trim())
                .personal(personal)
                .modalidade(modalidade)
                .horario(dto.horario())
                .alunos(alunosValidados)
                .build();

        turma = turmaRepository.save(turma);
        return toResponseDTO(turma);
    }

    @Transactional(readOnly = true)
    public List<TurmaResponseDTO> listarTurmas(Usuario usuario) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        return turmaRepository.findByPersonalId(personal.getId())
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public TurmaResponseDTO buscarPorId(Usuario usuario, UUID turmaId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        Turma turma = turmaRepository.findByIdAndPersonalId(turmaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Turma não encontrada."));
        return toResponseDTO(turma);
    }

    @Transactional
    public TurmaResponseDTO atualizarTurma(Usuario usuario, UUID turmaId, AtualizarTurmaRequestDTO dto) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        Turma turma = turmaRepository.findByIdAndPersonalId(turmaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Turma não encontrada."));

        Modalidade modalidade = modalidadeRepository.findById(dto.modalidadeId())
                .orElseThrow(() -> new NotFoundException("Modalidade não encontrada."));

        turma.setNome(dto.nome().trim());
        turma.setModalidade(modalidade);
        turma.setHorario(dto.horario());

        turma = turmaRepository.save(turma);
        return toResponseDTO(turma);
    }

    @Transactional
    public MensagemResponseDTO adicionarAluno(Usuario usuario, UUID turmaId, UUID alunoId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        Turma turma = turmaRepository.findByIdAndPersonalId(turmaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Turma não encontrada."));

        Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

        if (aluno.getStatus() == StatusConvite.INATIVO) {
            throw new BusinessException("Erro. Este aluno está inativo.");
        }

        boolean jaPertence = turma.getAlunos().stream()
                .anyMatch(a -> a.getId().equals(aluno.getId()));

        if (jaPertence) {
            throw new BusinessException("Aluno já pertence a essa turma");
        }

        turma.getAlunos().add(aluno);
        turmaRepository.save(turma);
        return new MensagemResponseDTO("Aluno adicionado com sucesso");
    }

    @Transactional
    public MensagemResponseDTO removerAluno(Usuario usuario, UUID turmaId, UUID alunoId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        Turma turma = turmaRepository.findByIdAndPersonalId(turmaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Turma não encontrada."));

        Aluno alunoRemover = turma.getAlunos().stream()
                .filter(a -> a.getId().equals(alunoId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado nesta turma."));

        turma.getAlunos().remove(alunoRemover);

        if (turma.getAlunos().isEmpty()) {
            turmaRepository.delete(turma);
        } else {
            turmaRepository.save(turma);
        }

        return new MensagemResponseDTO("Aluno removido com sucesso!");
    }

    private PersonalTrainer obterPersonalLogado(Usuario usuario) {
        return personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));
    }

    private TurmaResponseDTO toResponseDTO(Turma turma) {
        List<TurmaResponseDTO.AlunoResumoDTO> alunosDTO = turma.getAlunos().stream()
                .map(aluno -> new TurmaResponseDTO.AlunoResumoDTO(
                        aluno.getId(),
                        aluno.getUsuario() != null ? aluno.getUsuario().getNome() : null,
                        aluno.getUsuario() != null ? aluno.getUsuario().getEmail() : null
                ))
                .toList();

        return new TurmaResponseDTO(
                turma.getId(),
                turma.getNome(),
                turma.getModalidade().getId(),
                turma.getModalidade().getNome(),
                turma.getHorario(),
                alunosDTO.size(),
                alunosDTO
        );
    }
}