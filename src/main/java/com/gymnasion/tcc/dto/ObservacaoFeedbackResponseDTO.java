package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.TipoRegistroPerfil;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ObservacaoFeedbackResponseDTO(
        UUID id,
        UUID alunoId,
        UUID autorId,
        String nomeAutor,
        Role papelAutor,
        TipoRegistroPerfil tipo,
        String conteudo,
        OffsetDateTime dataCriacao
) {
}
