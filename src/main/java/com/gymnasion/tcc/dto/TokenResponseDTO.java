package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.Role;
import java.util.UUID;

public record TokenResponseDTO(
    String token,
    UUID id,
    Role role
) {
    public static TokenResponseDTO of(String token, UUID id, Role role) {
        return new TokenResponseDTO(token, id, role);
    }
}
