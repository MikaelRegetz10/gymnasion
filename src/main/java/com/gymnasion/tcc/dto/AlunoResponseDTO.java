package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.StatusConvite;

import java.util.Set;
import java.util.UUID;

public record AlunoResponseDTO(
    UUID id,
    UsuarioResponseDTO usuario,
    UUID personalId,
    String nomePersonal,
    StatusConvite status,
    Set<ModalidadeResponseDTO> modalidades
) {
}
