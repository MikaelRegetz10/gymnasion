package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.AtualizarMetricaRequestDTO;
import com.gymnasion.tcc.dto.CriarMetricaRequestDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.dto.MetricaResponseDTO;
import com.gymnasion.tcc.service.MetricaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers/metricas")
@AllArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Métricas Personalizadas", description = "Configuração e gestão de métricas de desempenho por modalidade desportiva")
@SecurityRequirement(name = "bearerAuth")
public class MetricaController {

    private final MetricaService metricaService;

    @PostMapping
    @Operation(summary = "Criar nova métrica personalizada (US-18)", description = "Cria um novo campo de métrica vinculado a uma modalidade e ao Personal Trainer.")
    @ApiResponse(responseCode = "201", description = "Métrica criada com sucesso.")
    @ApiResponse(responseCode = "409", description = "Já existe uma métrica com este nome na modalidade selecionada (RN-016).")
    public ResponseEntity<MetricaResponseDTO> criarMetrica(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody CriarMetricaRequestDTO dto) {
        MetricaResponseDTO response = metricaService.criarMetrica(usuario, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar métricas por modalidade (US-18)", description = "Retorna todas as métricas criadas para a modalidade especificada.")
    public ResponseEntity<List<MetricaResponseDTO>> listarPorModalidade(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam("modalidadeId") Long modalidadeId) {
        return ResponseEntity.ok(metricaService.listarMetricasPorModalidade(usuario, modalidadeId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar métrica (US-19)", description = "Atualiza o título ou tipo da métrica. Bloqueia alteração do tipo se já houver histórico (RN-017).")
    @ApiResponse(responseCode = "422", description = "Não é possível alterar o tipo de uma métrica com histórico.")
    public ResponseEntity<MetricaResponseDTO> atualizarMetrica(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody AtualizarMetricaRequestDTO dto) {
        return ResponseEntity.ok(metricaService.atualizarMetrica(id, usuario, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir ou descontinuar métrica (US-19)", description = "Realiza remoção física se não houver histórico, ou soft-delete preservando o histórico (RN-017).")
    public ResponseEntity<MensagemResponseDTO> excluirMetrica(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(metricaService.excluirMetrica(id, usuario));
    }
}