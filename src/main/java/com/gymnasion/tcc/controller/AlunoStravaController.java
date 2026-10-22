package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.StravaAtividadeDTO;
import com.gymnasion.tcc.dto.StravaAuthUrlResponseDTO;
import com.gymnasion.tcc.dto.StravaConectarRequestDTO;
import com.gymnasion.tcc.dto.StravaStatusResponseDTO;
import com.gymnasion.tcc.service.StravaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alunos/strava")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ALUNO')")
@Tag(name = "Strava (Aluno)", description = "Endpoints de integração com Strava OAuth 2.0 para alunos (US-14)")
@SecurityRequirement(name = "bearerAuth")
public class AlunoStravaController {

    private final StravaService stravaService;

    @GetMapping("/auth-url")
    @Operation(summary = "Obter URL de autorização OAuth 2.0 do Strava (US-14 C1)")
    public ResponseEntity<StravaAuthUrlResponseDTO> obterAuthUrl(
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(stravaService.obterAuthUrl(usuario));
    }

    @PostMapping("/conectar")
    @Operation(summary = "Conectar conta Strava com o code retornado no callback OAuth (US-14 C1)")
    public ResponseEntity<StravaStatusResponseDTO> conectar(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody StravaConectarRequestDTO dto) {
        return ResponseEntity.ok(stravaService.conectar(usuario, dto));
    }

    @GetMapping("/status")
    @Operation(summary = "Consultar status da conexão do Strava (US-14 C1/C3)")
    public ResponseEntity<StravaStatusResponseDTO> obterStatus(
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(stravaService.obterStatus(usuario));
    }

    @DeleteMapping("/desconectar")
    @Operation(summary = "Desconectar conta Strava do aluno")
    public ResponseEntity<Void> desconectar(
            @AuthenticationPrincipal Usuario usuario) {
        stravaService.desconectar(usuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/atividades")
    @Operation(summary = "Listar atividades recentes do Strava com métricas convertidas (US-14 C2)")
    public ResponseEntity<List<StravaAtividadeDTO>> listarAtividades(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "limite", required = false, defaultValue = "10") Integer limite) {
        return ResponseEntity.ok(stravaService.listarAtividadesRecentes(usuario, limite));
    }
}
