package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.ConviteResponseDTO;
import com.gymnasion.tcc.service.PersonalConviteService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/personal-trainer")
@AllArgsConstructor
public class PersonalTrainerController {

    private final PersonalConviteService conviteService;

    @PostMapping("/convite-aluno")
    public ResponseEntity<ConviteResponseDTO> gerarLinkConvite(@AuthenticationPrincipal Usuario usuario){
        return ResponseEntity.ok(conviteService.gerarLinkConvite(usuario));
    }
}
