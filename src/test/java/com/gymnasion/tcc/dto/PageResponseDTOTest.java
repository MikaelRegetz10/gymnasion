package com.gymnasion.tcc.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseDTOTest {

    @Test
    @DisplayName("PageResponseDTO.from(page) mapeia metadados e conteúdo corretamente")
    void from_pageDirectMapping() {
        PageRequest pageRequest = PageRequest.of(1, 2);
        Page<String> page = new PageImpl<>(List.of("item3", "item4"), pageRequest, 5);

        PageResponseDTO<String> dto = PageResponseDTO.from(page);

        assertThat(dto.content()).containsExactly("item3", "item4");
        assertThat(dto.pageNumber()).isEqualTo(1);
        assertThat(dto.pageSize()).isEqualTo(2);
        assertThat(dto.totalElements()).isEqualTo(5);
        assertThat(dto.totalPages()).isEqualTo(3);
        assertThat(dto.first()).isFalse();
        assertThat(dto.last()).isFalse();
    }

    @Test
    @DisplayName("PageResponseDTO.from(page, mapper) transforma os elementos com o mapper")
    void from_pageWithMapper() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Integer> page = new PageImpl<>(List.of(1, 2, 3), pageRequest, 3);

        PageResponseDTO<String> dto = PageResponseDTO.from(page, num -> "NUM-" + num);

        assertThat(dto.content()).containsExactly("NUM-1", "NUM-2", "NUM-3");
        assertThat(dto.pageNumber()).isEqualTo(0);
        assertThat(dto.pageSize()).isEqualTo(10);
        assertThat(dto.totalElements()).isEqualTo(3);
        assertThat(dto.totalPages()).isEqualTo(1);
        assertThat(dto.first()).isTrue();
        assertThat(dto.last()).isTrue();
    }
}
