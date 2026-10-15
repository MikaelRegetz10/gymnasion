package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.ObservacaoFeedback;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.domain.enums.TipoRegistroPerfil;
import com.gymnasion.tcc.dto.CriarObservacaoFeedbackRequestDTO;
import com.gymnasion.tcc.dto.ObservacaoFeedbackResponseDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.ObservacaoFeedbackRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Gestão de observações e feedbacks no perfil do aluno (US-26).
 * Registros são imutáveis (sem alteração ou exclusão - US-26 C5).
 */
@Service
@AllArgsConstructor
public class ObservacaoFeedbackService {

    private final ObservacaoFeedbackRepository observacaoFeedbackRepository;
    private final AlunoRepository alunoRepository;
    private final PersonalTrainerRepository personalTrainerRepository;

    @Transactional
    public ObservacaoFeedbackResponseDTO registrarFeedbackPeloPersonal(
            Usuario usuario,
            UUID alunoId,
            CriarObservacaoFeedbackRequestDTO dto
    ) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

        validarConteudo(dto);

        ObservacaoFeedback feedback = ObservacaoFeedback.builder()
                .aluno(aluno)
                .autor(usuario)
                .tipo(TipoRegistroPerfil.FEEDBACK)
                .conteudo(dto.conteudo().trim())
                .dataCriacao(OffsetDateTime.now())
                .build();

        feedback = observacaoFeedbackRepository.save(feedback);
        return toDTO(feedback);
    }

    @Transactional
    public ObservacaoFeedbackResponseDTO registrarObservacaoPeloAluno(
            Usuario usuario,
            CriarObservacaoFeedbackRequestDTO dto
    ) {
        Aluno aluno = alunoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        // RN-014: Aluno inativo não realiza registros
        if (aluno.getStatus() == StatusConvite.INATIVO) {
            throw new BusinessException("Erro. Este aluno está inativo.");
        }

        validarConteudo(dto);

        ObservacaoFeedback observacao = ObservacaoFeedback.builder()
                .aluno(aluno)
                .autor(usuario)
                .tipo(TipoRegistroPerfil.OBSERVACAO)
                .conteudo(dto.conteudo().trim())
                .dataCriacao(OffsetDateTime.now())
                .build();

        observacao = observacaoFeedbackRepository.save(observacao);
        return toDTO(observacao);
    }

    @Transactional
    public ObservacaoFeedbackResponseDTO registrar(
            Usuario usuario,
            UUID alunoId,
            CriarObservacaoFeedbackRequestDTO dto
    ) {
        if (usuario.getRole() == Role.PERSONAL_TRAINER) {
            return registrarFeedbackPeloPersonal(usuario, alunoId, dto);
        } else if (usuario.getRole() == Role.ALUNO) {
            Aluno aluno = alunoRepository.findByUsuario(usuario)
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

            if (!aluno.getId().equals(alunoId)) {
                throw new BusinessException("Você não tem permissão para registrar observação no perfil de outro aluno.");
            }
            return registrarObservacaoPeloAluno(usuario, dto);
        } else {
            throw new BusinessException("Papel de usuário não autorizado a registrar observações ou feedbacks.");
        }
    }

    @Transactional(readOnly = true)
    public List<ObservacaoFeedbackResponseDTO> listarObservacoesEFeedbacksPeloPersonal(
            Usuario usuario,
            UUID alunoId
    ) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

        return observacaoFeedbackRepository.findByAlunoIdOrderByDataCriacaoDesc(alunoId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ObservacaoFeedbackResponseDTO> listarObservacoesEFeedbacksPeloAluno(Usuario usuario) {
        Aluno aluno = alunoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        return observacaoFeedbackRepository.findByAlunoIdOrderByDataCriacaoDesc(aluno.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ObservacaoFeedbackResponseDTO> listarPorAluno(Usuario usuario, UUID alunoId) {
        if (usuario.getRole() == Role.PERSONAL_TRAINER) {
            return listarObservacoesEFeedbacksPeloPersonal(usuario, alunoId);
        } else if (usuario.getRole() == Role.ALUNO) {
            Aluno aluno = alunoRepository.findByUsuario(usuario)
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

            if (!aluno.getId().equals(alunoId)) {
                throw new BusinessException("Você não tem permissão para visualizar as observações de outro aluno.");
            }
            return observacaoFeedbackRepository.findByAlunoIdOrderByDataCriacaoDesc(alunoId)
                    .stream()
                    .map(this::toDTO)
                    .toList();
        } else {
            throw new BusinessException("Papel de usuário não autorizado.");
        }
    }

    private void validarConteudo(CriarObservacaoFeedbackRequestDTO dto) {
        if (dto == null || dto.conteudo() == null || dto.conteudo().isBlank()) {
            throw new BusinessException("O conteúdo é obrigatório.");
        }
    }

    private PersonalTrainer obterPersonalLogado(Usuario usuario) {
        return personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));
    }

    private ObservacaoFeedbackResponseDTO toDTO(ObservacaoFeedback entity) {
        return new ObservacaoFeedbackResponseDTO(
                entity.getId(),
                entity.getAluno().getId(),
                entity.getAutor().getId(),
                entity.getAutor().getNome(),
                entity.getAutor().getRole(),
                entity.getTipo(),
                entity.getConteudo(),
                entity.getDataCriacao()
        );
    }
}
