package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.dto.NotificacaoResponseDTO;
import com.gymnasion.tcc.service.NotificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alunos/notificacoes")
@AllArgsConstructor
@PreAuthorize("hasRole('ALUNO')")
@Tag(name = "Aluno - Notificações", description = "Central de notificações do Aluno")
@SecurityRequirement(name = "bearerAuth")
public class AlunoNotificacaoController {

    private final NotificacaoService notificacaoService;

    @GetMapping
    @Operation(summary = "Listar notificações do Aluno", description = "Retorna a lista de notificações do aluno logado.")
    public ResponseEntity<List<NotificacaoResponseDTO>> listarMinhasNotificacoes(
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(notificacaoService.listarPorUsuario(usuario));
    }

    @PatchMapping("/{id}/ler")
    @Operation(summary = "Marcar notificação como lida", description = "Marca a notificação do aluno como lida.")
    public ResponseEntity<NotificacaoResponseDTO> marcarComoLida(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID notificacaoId) {
        return ResponseEntity.ok(notificacaoService.marcarComoLida(usuario, notificacaoId));
    }

    @PatchMapping(value = {"/marcar-como-lidas", "/ler-todas"})
    @Operation(summary = "Marcar todas as notificações como lidas", description = "Marca todas as notificações do aluno como lidas.")
    public ResponseEntity<MensagemResponseDTO> marcarTodasComoLidas(
            @AuthenticationPrincipal Usuario usuario) {
        int total = notificacaoService.marcarTodasComoLidas(usuario);
        return ResponseEntity.ok(new MensagemResponseDTO(total + " notificação(ões) marcada(s) como lida(s)."));
    }

    @GetMapping("/contagem-nao-lidas")
    @Operation(summary = "Contagem de notificações não lidas", description = "Retorna o total de notificações não lidas para o badge.")
    public ResponseEntity<Long> contarNaoLidas(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(notificacaoService.contarNaoLidas(usuario));
    }
}
