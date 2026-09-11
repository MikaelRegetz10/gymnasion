package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequestDTO(
    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 150)
    String nome,

    @NotBlank(message = "O email é obrigatório.")
    @Email(message = "O email deve ser válido.")
    @Size(max = 100)
    String email,

    @NotBlank(message = "A senha é obrigatória.")
    @Size(min = 6, max = 255)
    String password,

    @NotBlank(message = "O CPF é obrigatório.")
    @Size(min = 11, max = 11)
    String cpf,

    @NotBlank(message = "O celular é obrigatório.")
    @Size(max = 13)
    String celular
) {}
