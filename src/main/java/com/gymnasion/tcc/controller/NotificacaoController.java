package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.AcompanhamentoTreinoService;
import com.gymnasion.tcc.service.NotificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers/notificacoes")
@AllArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Notificações (US-24)", description = "Central de notificações do Personal Trainer e pendências de métricas")
@SecurityRequirement(name = "bearerAuth")
public class NotificacaoController {

    private final AcompanhamentoTreinoService acompanhamentoTreinoService;
    private final NotificacaoService notificacaoService;

    @GetMapping
    @Operation(
            summary = "Listar notificações do Personal Trainer (US-24 C4)",
            description = "Retorna a lista paginada de notificações agrupadas por aluno com contagem de pendências."
    )
    @ApiResponse(responseCode = "200", description = "Lista de notificações retornada com sucesso.")
    public ResponseEntity<PageResponseDTO<NotificacaoResponseDTO>> listarMinhasNotificacoes(
            @AuthenticationPrincipal Usuario usuario,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(acompanhamentoTreinoService.listarNotificacoes(usuario, pageable)));
    }

    @PatchMapping("/{id}/ler")
    @Operation(
            summary = "Marcar notificação como lida (US-24 C7)",
            description = "Marca a notificação como lida. Conforme US-24 C7, apenas visualizar/ler a notificação não resolve a pendência de preenchimento da métrica."
    )
    @ApiResponse(responseCode = "200", description = "Notificação marcada como lida com sucesso.")
    @ApiResponse(responseCode = "404", description = "Notificação não encontrada.")
    public ResponseEntity<NotificacaoResponseDTO> marcarComoLida(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID notificacaoId) {
        return ResponseEntity.ok(acompanhamentoTreinoService.marcarComoLida(usuario, notificacaoId));
    }

    @PatchMapping(value = {"/marcar-como-lidas", "/ler-todas"})
    @Operation(
            summary = "Marcar todas as notificações como lidas",
            description = "Marca todas as notificações pendentes do Personal Trainer como lidas."
    )
    @ApiResponse(responseCode = "200", description = "Notificações marcadas como lidas com sucesso.")
    public ResponseEntity<MensagemResponseDTO> marcarTodasComoLidas(
            @AuthenticationPrincipal Usuario usuario) {
        int total = notificacaoService.marcarTodasComoLidas(usuario);
        return ResponseEntity.ok(new MensagemResponseDTO(total + " notificação(ões) marcada(s) como lida(s)."));
    }

    @GetMapping("/contagem-nao-lidas")
    @Operation(
            summary = "Contagem de notificações não lidas",
            description = "Retorna o total de notificações não lidas para o badge da barra de navegação."
    )
    public ResponseEntity<Long> contarNaoLidas(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(notificacaoService.contarNaoLidas(usuario));
    }
}
