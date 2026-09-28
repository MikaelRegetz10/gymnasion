package com.gymnasion.tcc.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TurmaResponseDTO(
        UUID id,
        String nome,
        Long modalidadeId,
        String nomeModalidade,
        OffsetDateTime horario,
        Integer quantidadeAlunos,
        List<AlunoResumoDTO> alunos
) {
    public record AlunoResumoDTO(
            UUID id,
            String nome,
            String email
    ) {}
}