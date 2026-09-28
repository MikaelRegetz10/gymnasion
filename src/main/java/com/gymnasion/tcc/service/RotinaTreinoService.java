package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.CriarRotinaTreinoRequestDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.dto.RotinaTreinoResponseDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class RotinaTreinoService {

    private final RotinaTreinoRepository rotinaTreinoRepository;
    private final ModalidadeRepository modalidadeRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final AlunoRepository alunoRepository;
    private final MetricaRepository metricaRepository;

    @Transactional
    public RotinaTreinoResponseDTO criarRotinaTreino(Usuario usuario, CriarRotinaTreinoRequestDTO dto) {
        PersonalTrainer personal = obterPersonalLogado(usuario);

        Modalidade modalidade = modalidadeRepository.findById(dto.modalidadeId())
                .orElseThrow(() -> new NotFoundException("Modalidade não encontrada."));

        // US-10 Cenário 2: Verifica se existem métricas cadastradas para a modalidade antes de continuar
        List<Metrica> metricasDaModalidade = metricaRepository.findByPersonalTrainerIdAndModalidadeId(
                personal.getId(), dto.modalidadeId()
        );
        if (metricasDaModalidade.isEmpty()) {
            throw new BusinessException("Nenhuma métrica cadastrada para essa modalidade. Cadastre ao menos uma métrica antes de continuar.");
        }

        // Validação e associação das métricas selecionadas
        Set<Metrica> metricasValidadas = new HashSet<>();
        for (UUID metricaId : dto.metricasIds()) {
            Metrica metrica = metricaRepository.findByIdAndPersonalTrainerId(metricaId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Métrica com ID " + metricaId + " não foi encontrada para este Personal Trainer."));

            if (!metrica.getModalidade().getId().equals(modalidade.getId())) {
                throw new BusinessException("A métrica '" + metrica.getTitulo() + "' não pertence à modalidade selecionada.");
            }
            metricasValidadas.add(metrica);
        }

        Set<Aluno> alunosValidados = new HashSet<>();
        for (UUID alunoId : dto.alunosIds()) {
            Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

            if (aluno.getStatus() == StatusConvite.INATIVO) {
                throw new BusinessException("Erro. Este aluno está inativo.");
            }
            alunosValidados.add(aluno);
        }

        RotinaTreino rotina = RotinaTreino.builder()
                .titulo(dto.titulo().trim())
                .modalidade(modalidade)
                .frequenciaSemanal(dto.frequenciaSemanal())
                .objetivos(dto.objetivos())
                .personalTrainer(personal)
                .alunos(alunosValidados)
                .metricas(metricasValidadas)
                .build();

        rotina = rotinaTreinoRepository.save(rotina);
        return toResponseDTO(rotina);
    }

    @Transactional(readOnly = true)
    public List<RotinaTreinoResponseDTO> listarPorPersonal(Usuario usuario) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        return rotinaTreinoRepository.findByPersonalTrainerId(personal.getId())
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RotinaTreinoResponseDTO> listarPorAluno(Usuario usuario, UUID alunoId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        return rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personal.getId(), alunoId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public RotinaTreinoResponseDTO buscarPorId(Usuario usuario, UUID rotinaId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        RotinaTreino rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));
        return toResponseDTO(rotina);
    }

    @Transactional
    public MensagemResponseDTO excluirRotina(Usuario usuario, UUID rotinaId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        RotinaTreino rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));

        rotinaTreinoRepository.delete(rotina);
        return new MensagemResponseDTO("Rotina excluída com sucesso!");
    }

    private PersonalTrainer obterPersonalLogado(Usuario usuario) {
        return personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));
    }

    private RotinaTreinoResponseDTO toResponseDTO(RotinaTreino rotina) {
        List<RotinaTreinoResponseDTO.AlunoResumoDTO> alunosDTO = rotina.getAlunos().stream()
                .map(aluno -> new RotinaTreinoResponseDTO.AlunoResumoDTO(
                        aluno.getId(),
                        aluno.getUsuario() != null ? aluno.getUsuario().getNome() : null
                ))
                .toList();

        List<RotinaTreinoResponseDTO.MetricaResumoDTO> metricasDTO = rotina.getMetricas().stream()
                .map(metrica -> new RotinaTreinoResponseDTO.MetricaResumoDTO(
                        metrica.getId(),
                        metrica.getTitulo(),
                        metrica.getTipo()
                ))
                .toList();

        return new RotinaTreinoResponseDTO(
                rotina.getId(),
                rotina.getTitulo(),
                rotina.getModalidade().getId(),
                rotina.getModalidade().getNome(),
                rotina.getFrequenciaSemanal(),
                rotina.getObjetivos(),
                alunosDTO,
                metricasDTO
        );
    }
}