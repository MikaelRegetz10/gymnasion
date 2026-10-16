package com.gymnasion.tcc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "Estrutura padrão de resposta paginada do sistema")
public record PageResponseDTO<T>(
        @Schema(description = "Itens da página atual")
        List<T> content,

        @Schema(description = "Número da página atual (iniciando em 0)", example = "0")
        int pageNumber,

        @Schema(description = "Quantidade de itens por página", example = "10")
        int pageSize,

        @Schema(description = "Total de registros encontrados no banco", example = "42")
        long totalElements,

        @Schema(description = "Total de páginas disponíveis", example = "5")
        int totalPages,

        @Schema(description = "Indica se é a primeira página", example = "true")
        boolean first,

        @Schema(description = "Indica se é a última página", example = "false")
        boolean last
) {
    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return new PageResponseDTO<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public static <E, D> PageResponseDTO<D> from(Page<E> page, Function<E, D> mapper) {
        return new PageResponseDTO<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
