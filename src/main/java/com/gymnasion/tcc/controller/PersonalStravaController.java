package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.StravaAtividadeDTO;
import com.gymnasion.tcc.dto.StravaStatusResponseDTO;
import com.gymnasion.tcc.service.StravaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers/alunos/{alunoId}/strava")
@RequiredArgsConstructor
@Tag(name = "Strava (Personal)", description = "Visualização de atividades sincronizadas do Strava pelo Personal Trainer (US-14)")
@SecurityRequirement(name = "bearerAuth")
public class PersonalStravaController {

    private final StravaService stravaService;

    @GetMapping("/status")
    @Operation(summary = "Consultar status de conexão do Strava de um aluno")
    public ResponseEntity<StravaStatusResponseDTO> obterStatus(
            @PathVariable UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(stravaService.obterStatusPorPersonal(alunoId, usuario.getId()));
    }

    @GetMapping("/atividades")
    @Operation(summary = "Listar atividades recentes do aluno sincronizadas pelo Strava")
    public ResponseEntity<List<StravaAtividadeDTO>> listarAtividades(
            @PathVariable UUID alunoId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "limite", required = false, defaultValue = "10") Integer limite) {
        return ResponseEntity.ok(stravaService.listarAtividadesPorPersonal(alunoId, usuario.getId(), limite));
    }
}
