package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.PersonalConvites;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.ConviteResponseDTO;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.PersonalConvitesRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import com.gymnasion.tcc.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PersonalConviteService {

    private final AlunoRepository alunoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final PersonalConvitesRepository personalConviteRepository;

    @Transactional
    public ConviteResponseDTO gerarLinkConvite(Usuario usuario){
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Personal não encontrado!"));

        String token = UUID.randomUUID().toString();
        PersonalConvites invitation = PersonalConvites.builder()
                .token(token)
                .personalId(personalTrainer.getId())
                .status(StatusConvite.ATIVO)
                .maximoUsuarios(30)
                .quantidadesUsuarios(0)
                .dataExpiracao(LocalDateTime.now().plusDays(7))
                .build();

        personalConviteRepository.save(invitation);

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String fullUrl = baseUrl + "/invite?token=" + token;

        return new ConviteResponseDTO(fullUrl);
    }

}
