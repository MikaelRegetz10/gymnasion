package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.Role;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UsuarioPerfilResponseDTO(
        UUID id,
        String nome,
        String email,
        String cpf,
        String celular,
        Role role,
        String modalidadePrincipal,
        Long modalidadePrincipalId,
        List<String> modalidadesAluno,
        OffsetDateTime dataCriacao
) {}
