package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.AtualizarMetricaRequestDTO;
import com.gymnasion.tcc.dto.CriarMetricaRequestDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.dto.MetricaResponseDTO;
import com.gymnasion.tcc.service.MetricaService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers/metricas")
@AllArgsConstructor
public class MetricaController {

    private final MetricaService metricaService;

    @PostMapping
    public ResponseEntity<MetricaResponseDTO> criarMetrica(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody CriarMetricaRequestDTO dto) {
        MetricaResponseDTO response = metricaService.criarMetrica(usuario, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MetricaResponseDTO>> listarPorModalidade(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam("modalidadeId") Long modalidadeId) {
        return ResponseEntity.ok(metricaService.listarMetricasPorModalidade(usuario, modalidadeId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MetricaResponseDTO> atualizarMetrica(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody AtualizarMetricaRequestDTO dto) {
        return ResponseEntity.ok(metricaService.atualizarMetrica(id, usuario, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MensagemResponseDTO> excluirMetrica(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(metricaService.excluirMetrica(id, usuario));
    }
}