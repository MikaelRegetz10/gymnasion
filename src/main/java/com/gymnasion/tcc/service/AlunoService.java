package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.AlunoResponseDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.dto.ModalidadeResponseDTO;
import com.gymnasion.tcc.dto.UsuarioResponseDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import com.gymnasion.tcc.event.SolicitacaoAlunoAvaliadaEvent;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AlunoService {

    private final AlunoRepository alunoRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final NotificacaoService notificacaoService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public AlunoResponseDTO obterMeuPerfil(Usuario usuario) {
        Aluno aluno = alunoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));
        return toAlunoResponseDTO(aluno);
    }

    @Transactional(readOnly = true)
    public Page<AlunoResponseDTO> getAlunosVinculados(Usuario usuario, org.springframework.data.domain.Pageable pageable) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Personal não encontrado!"));

        return alunoRepository.findByPersonalId(personalTrainer.getId(), pageable)
                .map(this::toAlunoResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<AlunoResponseDTO> getAlunosVinculados(Usuario usuario) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Personal não encontrado!"));

        return alunoRepository.findByPersonalId(personalTrainer.getId())
                .stream()
                .map(this::toAlunoResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AlunoResponseDTO> getAlunosInativos(Usuario usuarioLogado, org.springframework.data.domain.Pageable pageable) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new UsernameNotFoundException("Personal Trainer não encontrado!"));

        return alunoRepository.findByPersonalIdAndStatus(personal.getId(), StatusConvite.INATIVO, pageable)
                .map(this::toAlunoResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<AlunoResponseDTO> getAlunosInativos(Usuario usuarioLogado) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new UsernameNotFoundException("Personal Trainer não encontrado!"));

        return alunoRepository.findByPersonalIdAndStatus(personal.getId(), StatusConvite.INATIVO)
                .stream()
                .map(this::toAlunoResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AlunoResponseDTO> getAlunosPendentes(Usuario usuario, org.springframework.data.domain.Pageable pageable) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Personal não encontrado!"));

        return alunoRepository.findByPersonalIdAndStatus(personalTrainer.getId(), StatusConvite.PENDENTE, pageable)
                .map(this::toAlunoResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<AlunoResponseDTO> getAlunosPendentes(Usuario usuario){
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Personal não encontrado!"));

        return alunoRepository.findByPersonalIdAndStatus(personalTrainer.getId(), StatusConvite.PENDENTE)
                .stream()
                .map(this::toAlunoResponseDTO)
                .toList();
    }

    @Transactional
    public MensagemResponseDTO desativarAluno(UUID alunoId, Usuario usuarioLogado) {
        Aluno aluno = validarEPegarAlunoDoPersonal(alunoId, usuarioLogado);

        aluno.setStatus(StatusConvite.INATIVO);
        alunoRepository.save(aluno);

        return new MensagemResponseDTO("Aluno desativado com sucesso");
    }

    @Transactional
    public MensagemResponseDTO reativarAluno(UUID alunoId, Usuario usuarioLogado) {
        Aluno aluno = validarEPegarAlunoDoPersonal(alunoId, usuarioLogado);

        aluno.setStatus(StatusConvite.ATIVO);
        alunoRepository.save(aluno);

        return new MensagemResponseDTO("Aluno reativado com sucesso!");
    }

    @Transactional
    public AlunoResponseDTO aprovarAluno(UUID alunoId, Usuario usuarioLogado) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado!"));

        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        if (!aluno.getPersonal().getId().equals(personalTrainer.getId())) {
            throw new BusinessException("Você não tem permissão para gerenciar este aluno.");
        }

        if (aluno.getStatus() != StatusConvite.PENDENTE) {
            throw new BusinessException("Este aluno não está pendente de aprovação.");
        }

        aluno.setStatus(StatusConvite.ATIVO);
        aluno = alunoRepository.save(aluno);

        String nomePersonal = personalTrainer.getUsuario().getNome();

        // US-03 C3: notificação in-app para o aluno
        notificacaoService.notificarSolicitacaoAprovada(aluno, nomePersonal);

        // Personal: solicitação tratada -> marca "Nova solicitação" como lida / remove lembrete se zerou
        notificacaoService.resolverSolicitacao(personalTrainer, aluno.getId(), semAlunosPendentes(personalTrainer));

        // US-03 C3: e-mail automático ao aluno (enviado após o commit)
        eventPublisher.publishEvent(new SolicitacaoAlunoAvaliadaEvent(
                aluno.getUsuario().getEmail(),
                aluno.getUsuario().getNome(),
                nomePersonal,
                true
        ));

        return toAlunoResponseDTO(aluno);
    }

    @Transactional
    public MensagemResponseDTO recusarAluno(UUID alunoId, Usuario usuarioLogado) {
        PersonalTrainer personalTrainer = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado!"));

        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        if (!aluno.getPersonal().getId().equals(personalTrainer.getId())) {
            throw new BusinessException("Você não tem permissão para gerenciar este aluno.");
        }

        if (aluno.getStatus() != StatusConvite.PENDENTE) {
            throw new BusinessException("Este aluno não está pendente de aprovação.");
        }

        // Captura os dados antes da exclusão para o e-mail
        String emailAluno = aluno.getUsuario().getEmail();
        String nomeAluno = aluno.getUsuario().getNome();
        String nomePersonal = personalTrainer.getUsuario().getNome();

        alunoRepository.delete(aluno);
        alunoRepository.flush();

        // Personal: solicitação tratada -> marca "Nova solicitação" como lida / remove lembrete se zerou
        notificacaoService.resolverSolicitacao(personalTrainer, alunoId, semAlunosPendentes(personalTrainer));

        // US-03 C4: e-mail automático ao aluno (enviado após o commit)
        eventPublisher.publishEvent(new SolicitacaoAlunoAvaliadaEvent(
                emailAluno,
                nomeAluno,
                nomePersonal,
                false
        ));

        return new MensagemResponseDTO("Aluno " + nomeAluno + " foi recusado com sucesso!");
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

    private boolean semAlunosPendentes(PersonalTrainer personal) {
        return alunoRepository.countByPersonalIdAndStatus(personal.getId(), StatusConvite.PENDENTE) == 0;
    }

    private Aluno validarEPegarAlunoDoPersonal(UUID alunoId, Usuario usuarioLogado) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuarioLogado)
                .orElseThrow(() -> new UsernameNotFoundException("Personal Trainer não encontrado!"));

        return alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));
    }

}
