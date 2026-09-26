package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.AlunoResponseDTO;
import com.gymnasion.tcc.dto.ModalidadeResponseDTO;
import com.gymnasion.tcc.dto.UsuarioResponseDTO;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AlunoService {

    private final AlunoRepository alunoRepository;
    private final PersonalTrainerRepository personalTrainerRepository;

    @Transactional(readOnly = true)
    public List<AlunoResponseDTO> getAlunosVinculados(Usuario usuario) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal não encontrado!"));

        return alunoRepository.findByPersonalId(personalTrainer.getId())
                .stream()
                .map(this::toAlunoResponseDTO)
                .toList();
    }

    private AlunoResponseDTO toAlunoResponseDTO(Aluno aluno) {
        Set<ModalidadeResponseDTO> modalidadesDTO = aluno.getModalidades().stream()
                .map(m -> new ModalidadeResponseDTO(m.getId(), m.getNome(), m.getDescricao()))
                .collect(Collectors.toSet());

        UsuarioResponseDTO usuarioDTO = new UsuarioResponseDTO(
                aluno.getUsuario().getId(),
                aluno.getUsuario().getNome(),
                aluno.getUsuario().getEmail(),
                aluno.getUsuario().getCpf(),
                aluno.getUsuario().getCelular(),
                aluno.getUsuario().getRole()
        );

        return new AlunoResponseDTO(
                aluno.getId(),
                usuarioDTO,
                aluno.getPersonal().getId(),
                aluno.getPersonal().getUsuario().getNome(),
                aluno.getStatus(),
                modalidadesDTO
        );
    }


}
