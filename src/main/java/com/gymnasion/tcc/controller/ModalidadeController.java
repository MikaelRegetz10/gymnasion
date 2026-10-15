package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.dto.ModalidadeRequestDTO;
import com.gymnasion.tcc.dto.ModalidadeResponseDTO;
import com.gymnasion.tcc.dto.PageResponseDTO;
import com.gymnasion.tcc.service.ModalidadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/modalidades")
@AllArgsConstructor
@Tag(name = "Modalidades Esportivas", description = "Consulta e gestão do catálogo de modalidades desportivas do sistema")
public class ModalidadeController {

    private final ModalidadeService modalidadeService;

    @GetMapping
    @Operation(summary = "Listar todas as modalidades", description = "Retorna a lista de modalidades desportivas disponíveis de forma paginada.")
    public ResponseEntity<PageResponseDTO<ModalidadeResponseDTO>> listarTodas(
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(modalidadeService.listarTodas(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar modalidade por ID", description = "Retorna os detalhes de uma modalidade específica.")
    public ResponseEntity<ModalidadeResponseDTO> buscarPorId(@PathVariable("id") Long id) {
        return ResponseEntity.ok(modalidadeService.buscarPorId(id));
    }

    @PostMapping
    @Operation(summary = "Criar nova modalidade", description = "Adiciona uma nova modalidade desportiva ao sistema.")
    public ResponseEntity<ModalidadeResponseDTO> criar(@Valid @RequestBody ModalidadeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(modalidadeService.criar(dto));
    }
}