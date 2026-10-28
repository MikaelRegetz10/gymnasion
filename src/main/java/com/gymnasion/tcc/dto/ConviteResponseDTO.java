package com.gymnasion.tcc.dto;

public record ConviteResponseDTO(
        String url,
        String emailEnviado
) {
    public ConviteResponseDTO(String url) {
        this(url, null);
    }
}
