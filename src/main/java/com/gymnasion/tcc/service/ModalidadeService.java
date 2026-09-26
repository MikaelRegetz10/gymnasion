package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Modalidade;
import com.gymnasion.tcc.dto.ModalidadeRequestDTO;
import com.gymnasion.tcc.dto.ModalidadeResponseDTO;
import com.gymnasion.tcc.exceptions.DuplicateResourceException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.ModalidadeRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class ModalidadeService {

    private final ModalidadeRepository modalidadeRepository;

    @Transactional(readOnly = true)
    public List<ModalidadeResponseDTO> listarTodas() {
        return modalidadeRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ModalidadeResponseDTO buscarPorId(Long id) {
        Modalidade modalidade = modalidadeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Modalidade não encontrada."));
        return toDTO(modalidade);
    }

    @Transactional
    public ModalidadeResponseDTO criar(ModalidadeRequestDTO dto) {
        if (modalidadeRepository.existsByNomeIgnoreCase(dto.nome().trim())) {
            throw new DuplicateResourceException("Já existe uma modalidade cadastrada com esse nome.");
        }

        Modalidade modalidade = Modalidade.builder()
                .nome(dto.nome().trim())
                .descricao(dto.descricao() != null ? dto.descricao().trim() : null)
                .build();

        modalidade = modalidadeRepository.save(modalidade);
        return toDTO(modalidade);
    }

    private ModalidadeResponseDTO toDTO(Modalidade modalidade) {
        return new ModalidadeResponseDTO(
                modalidade.getId(),
                modalidade.getNome(),
                modalidade.getDescricao()
        );
    }
}