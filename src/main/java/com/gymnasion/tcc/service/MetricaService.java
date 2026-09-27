package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Metrica;
import com.gymnasion.tcc.domain.Modalidade;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.AtualizarMetricaRequestDTO;
import com.gymnasion.tcc.dto.CriarMetricaRequestDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.dto.MetricaResponseDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.DuplicateResourceException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.MetricaRepository;
import com.gymnasion.tcc.repository.ModalidadeRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import com.gymnasion.tcc.repository.RegistroMetricaRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class MetricaService {

    private final MetricaRepository metricaRepository;
    private final ModalidadeRepository modalidadeRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final RegistroMetricaRepository registroMetricaRepository;

    @Transactional
    public MetricaResponseDTO criarMetrica(Usuario usuario, CriarMetricaRequestDTO dto) {
        PersonalTrainer personal = obterPersonalLogado(usuario);

        boolean existeDuplicado = metricaRepository.existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCase(
                personal.getId(), dto.modalidadeId(), dto.titulo().trim()
        );

        if (existeDuplicado) {
            throw new DuplicateResourceException("Já existe uma métrica com esse nome nessa modalidade.");
        }

        Modalidade modalidade = modalidadeRepository.findById(dto.modalidadeId())
                .orElseThrow(() -> new NotFoundException("Modalidade não encontrada."));

        Metrica metrica = Metrica.builder()
                .titulo(dto.titulo().trim())
                .tipo(dto.tipo())
                .modalidade(modalidade)
                .personalTrainer(personal)
                .ativo(true)
                .build();

        metrica = metricaRepository.save(metrica);

        return toResponseDTO(metrica, 0L);
    }

    @Transactional(readOnly = true)
    public List<MetricaResponseDTO> listarMetricasPorModalidade(Usuario usuario, Long modalidadeId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);

        return metricaRepository.findByPersonalTrainerIdAndModalidadeId(personal.getId(), modalidadeId)
                .stream()
                .map(metrica -> {
                    Long qtdAlunos = registroMetricaRepository.countAlunosComRegistrosByMetricaId(metrica.getId());
                    return toResponseDTO(metrica, qtdAlunos);
                })
                .toList();
    }

    @Transactional
    public MetricaResponseDTO atualizarMetrica(UUID metricaId, Usuario usuario, AtualizarMetricaRequestDTO dto) {
        PersonalTrainer personal = obterPersonalLogado(usuario);

        Metrica metrica = metricaRepository.findByIdAndPersonalTrainerId(metricaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Métrica não encontrada ou não pertence a este Personal Trainer."));

        boolean possuiHistorico = registroMetricaRepository.existsByMetricaId(metricaId);

        if (dto.tipo() != null && dto.tipo() != metrica.getTipo()) {
            if (possuiHistorico) {
                throw new BusinessException("Não é possível alterar o tipo de uma métrica com histórico de registros. Crie uma nova métrica.");
            }
            metrica.setTipo(dto.tipo());
        }

        if (!metrica.getTitulo().equalsIgnoreCase(dto.titulo().trim())) {
            boolean tituloEmUso = metricaRepository.existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCaseAndIdNot(
                    personal.getId(), metrica.getModalidade().getId(), dto.titulo().trim(), metricaId
            );

            if (tituloEmUso) {
                throw new DuplicateResourceException("Já existe uma métrica com esse nome nessa modalidade.");
            }
            metrica.setTitulo(dto.titulo().trim());
        }

        metrica = metricaRepository.save(metrica);

        Long qtdAlunos = registroMetricaRepository.countAlunosComRegistrosByMetricaId(metricaId);
        return toResponseDTO(metrica, qtdAlunos);
    }

    @Transactional
    public MensagemResponseDTO excluirMetrica(UUID metricaId, Usuario usuario) {
        PersonalTrainer personal = obterPersonalLogado(usuario);

        Metrica metrica = metricaRepository.findByIdAndPersonalTrainerId(metricaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Métrica não encontrada ou não pertence a este Personal Trainer."));

        boolean possuiHistorico = registroMetricaRepository.existsByMetricaId(metricaId);

        if (possuiHistorico) {
            metrica.setAtivo(false);
            metricaRepository.save(metrica);
            return new MensagemResponseDTO("Métrica descontinuada com sucesso. O histórico foi mantido.");
        } else {
            metricaRepository.delete(metrica);
            return new MensagemResponseDTO("Métrica excluída com sucesso!");
        }
    }

    private PersonalTrainer obterPersonalLogado(Usuario usuario) {
        return personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));
    }

    private MetricaResponseDTO toResponseDTO(Metrica metrica, Long qtdAlunos) {
        return new MetricaResponseDTO(
                metrica.getId(),
                metrica.getTitulo(),
                metrica.getTipo(),
                metrica.getModalidade().getId(),
                metrica.getModalidade().getNome(),
                metrica.getAtivo(),
                qtdAlunos
        );
    }
}