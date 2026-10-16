package com.gymnasion.tcc.dto;

public record RelatorioPdfDTO(
        String nomeArquivo,
        byte[] conteudo
) {}
