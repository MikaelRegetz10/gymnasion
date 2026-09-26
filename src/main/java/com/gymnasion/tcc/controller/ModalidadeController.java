package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.dto.ModalidadeRequestDTO;
import com.gymnasion.tcc.dto.ModalidadeResponseDTO;
import com.gymnasion.tcc.service.ModalidadeService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/modalidades")
@AllArgsConstructor
public class ModalidadeController {

    private final ModalidadeService modalidadeService;

    @GetMapping
    public ResponseEntity<List<ModalidadeResponseDTO>> listarTodas() {
        return ResponseEntity.ok(modalidadeService.listarTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModalidadeResponseDTO> buscarPorId(@PathVariable("id") Long id) {
        return ResponseEntity.ok(modalidadeService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ModalidadeResponseDTO> criar(@Valid @RequestBody ModalidadeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(modalidadeService.criar(dto));
    }
}