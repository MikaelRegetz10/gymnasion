package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.AlunoResponseDTO;
import com.gymnasion.tcc.dto.ConviteResponseDTO;
import com.gymnasion.tcc.dto.GerarConviteRequestDTO;
import com.gymnasion.tcc.service.PersonalConviteService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personal-trainer")
@AllArgsConstructor
public class PersonalTrainerController {

    private final PersonalConviteService conviteService;

    @PostMapping("/convite-aluno")
    public ResponseEntity<ConviteResponseDTO> gerarLinkConvite(@AuthenticationPrincipal Usuario usuario, @Valid @RequestBody GerarConviteRequestDTO dto){
        return ResponseEntity.ok(conviteService.gerarLinkConvite(usuario, dto));
    }

    @GetMapping("/alunos/pendentes")
    public ResponseEntity<List<AlunoResponseDTO>> getAlunosPendentes(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(conviteService.getAlunosPendentes(usuario));
    }

    @PatchMapping("/atletas/{alunoId}/aprovar")
    public ResponseEntity<AlunoResponseDTO> aprovarAluno(
            @PathVariable("alunoId") UUID alunoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(conviteService.aprovarAluno(alunoId, usuario));
    }
}
