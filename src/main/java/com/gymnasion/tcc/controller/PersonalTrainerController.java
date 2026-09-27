package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.AlunoResponseDTO;
import com.gymnasion.tcc.dto.ConviteResponseDTO;
import com.gymnasion.tcc.dto.GerarConviteRequestDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.service.AlunoService;
import com.gymnasion.tcc.service.PersonalConviteService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers")
@AllArgsConstructor
public class PersonalTrainerController {

    private final PersonalConviteService conviteService;
    private final AlunoService alunoService;

    @PostMapping("/convites")
    public ResponseEntity<ConviteResponseDTO> gerarLinkConvite(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody GerarConviteRequestDTO dto) {
        ConviteResponseDTO convite = conviteService.gerarLinkConvite(usuario, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(convite);
    }

    @GetMapping("/alunos")
    public ResponseEntity<List<AlunoResponseDTO>> getAlunos(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.getAlunosVinculados(usuario));
    }

    @GetMapping("/alunos/pendentes")
    public ResponseEntity<List<AlunoResponseDTO>> getAlunosPendentes(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.getAlunosPendentes(usuario));
    }

    @GetMapping("/alunos/inativos")
    public ResponseEntity<List<AlunoResponseDTO>> getAlunosInativos(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.getAlunosInativos(usuario));
    }

    @PatchMapping("/alunos/{alunoId}/aprovar")
    public ResponseEntity<AlunoResponseDTO> aprovarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.aprovarAluno(alunoId, usuario));
    }

    @PatchMapping("/alunos/{alunoId}/recusar")
    public ResponseEntity<Void> recusarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        alunoService.recusarAluno(alunoId, usuario);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/alunos/{alunoId}/desativar")
    public ResponseEntity<MensagemResponseDTO> desativarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.desativarAluno(alunoId, usuario));
    }

    @PatchMapping("/alunos/{alunoId}/reativar")
    public ResponseEntity<MensagemResponseDTO> reativarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(alunoService.reativarAluno(alunoId, usuario));
    }
}