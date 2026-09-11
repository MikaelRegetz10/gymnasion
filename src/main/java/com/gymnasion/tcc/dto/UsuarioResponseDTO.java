package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.Role;
import java.util.UUID;

public record UsuarioResponseDTO(
    UUID id,
    String nome,
    String email,
    String cpf,
    String celular,
    Role role
) {}
