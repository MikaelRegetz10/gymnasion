package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.Notificacao;
import com.gymnasion.tcc.domain.PersonalConvites;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.SessaoTreino;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.TipoNotificacao;
import com.gymnasion.tcc.dto.NotificacaoResponseDTO;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Centraliza a criação das notificações in-app (central de notificações).
 * Notificações de pendências de métricas (US-24) continuam em {@link AcompanhamentoTreinoService}.
 */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;

    /**
     * Personal: aluno se registrou pelo link de convite (US-03 / US-04 C2).
     */
    @Transactional
    public void notificarNovaSolicitacao(PersonalTrainer personal, Aluno aluno) {
        salvar(personal.getUsuario(),
                "Nova solicitação",
                aluno.getUsuario().getNome() + " usou seu link de convite.",
                TipoNotificacao.NOVA_SOLICITACAO,
                aluno.getId());
    }

    /**
     * Personal: a solicitação foi tratada (aprovada/recusada) -> notificação marcada como lida.
     * Se não restarem alunos pendentes, o lembrete de pendências é removido.
     */
    @Transactional
    public void resolverSolicitacao(PersonalTrainer personal, UUID alunoId, boolean semPendenciasRestantes) {
        UUID usuarioId = personal.getUsuario().getId();
        notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(usuarioId, TipoNotificacao.NOVA_SOLICITACAO, alunoId)
                .ifPresent(n -> {
                    n.setLida(true);
                    notificacaoRepository.save(n);
                });

        if (semPendenciasRestantes) {
            notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(usuarioId, TipoNotificacao.ALUNOS_PENDENTES, personal.getId())
                    .ifPresent(notificacaoRepository::delete);
        }
    }

    /**
     * Aluno: solicitação aprovada (US-03 C3).
     */
    @Transactional
    public void notificarSolicitacaoAprovada(Aluno aluno, String nomePersonal) {
        salvar(aluno.getUsuario(),
                "Solicitação aprovada",
                "O " + nomePersonal + " aprovou sua solicitação, agora você pode acessar o sistema para conferir seus treinos.",
                TipoNotificacao.SOLICITACAO_APROVADA,
                aluno.getId());
    }

    /**
     * Personal: aluno concluiu uma sessão de treino (US-10 C5/C6) — permite ao personal validar a execução.
     * Se a mesma sessão for registrada novamente, a notificação existente é atualizada (sem duplicar).
     */
    @Transactional
    public void notificarTreinoConcluido(Aluno aluno, SessaoTreino sessao) {
        PersonalTrainer personal = aluno.getPersonal();
        if (personal == null || personal.getUsuario() == null) {
            return;
        }

        String nomeAluno = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : "Aluno";
        String tituloRotina = sessao.getRotinaTreino() != null ? sessao.getRotinaTreino().getTitulo() : "rotina";
        String mensagem = nomeAluno + " concluiu o treino da semana " + sessao.getSemana()
                + " da rotina \"" + tituloRotina + "\".";

        upsert(personal.getUsuario(), "Treino concluído", mensagem, TipoNotificacao.TREINO_CONCLUIDO, sessao.getId());
    }

    /**
     * Personal: link de convite atingiu o limite de utilizações.
     */
    @Transactional
    public void notificarConviteEsgotado(PersonalTrainer personal, PersonalConvites convite) {
        String modalidade = convite.getModalidade() != null ? " de " + convite.getModalidade().getNome() : "";
        salvar(personal.getUsuario(),
                "Link de convite esgotado",
                "Seu link de convite" + modalidade + " atingiu o limite de " + convite.getMaximoUsuarios()
                        + " cadastros. Gere um novo link para convidar mais alunos.",
                TipoNotificacao.CONVITE_ESGOTADO,
                convite.getId());
    }

    /**
     * US-14 C3: Token do Strava expirou ("Reconecte o Strava").
     */
    @Transactional
    public void notificarStravaExpirado(Usuario usuario) {
        upsert(usuario,
                "Reconecte o Strava",
                "O token de acesso expirou. A sincronização está pausada.",
                TipoNotificacao.STRAVA_EXPIRADO,
                usuario.getId());
    }

    /**
     * US-14 C3: Strava reconectado ou desconectado; a notificação de reconexão deixa de estar pendente.
     */
    @Transactional
    public void resolverStravaExpirado(Usuario usuario) {
        notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(usuario.getId(), TipoNotificacao.STRAVA_EXPIRADO, usuario.getId())
                .ifPresent(n -> {
                    n.setLida(true);
                    notificacaoRepository.save(n);
                });
    }

    /**
     * US-16 C1: Relatório de performance gerado e disponível para download ("Relatório pronto").
     */
    @Transactional
    public void notificarRelatorioPronto(Usuario usuario, String nomeAluno, UUID relatorioId) {
        salvar(usuario,
                "Relatório pronto",
                "Relatório de " + nomeAluno + " disponível para download.",
                TipoNotificacao.RELATORIO_PRONTO,
                relatorioId);
    }

    @Transactional(readOnly = true)
    public Optional<Notificacao> buscarLembreteAlunosPendentes(PersonalTrainer personal) {
        return notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(
                personal.getUsuario().getId(), TipoNotificacao.ALUNOS_PENDENTES, personal.getId());
    }

    /**
     * Personal: possui alunos aguardando aprovação há mais que o tempo limite configurado.
     * Mantém uma única notificação por personal, atualizada a cada lembrete.
     */
    @Transactional
    public void registrarLembreteAlunosPendentes(PersonalTrainer personal, int quantidade, Duration tempoLimite) {
        String mensagem = "Você possui " + quantidade + " aluno(s) aguardando aprovação há mais de "
                + formatarDuracao(tempoLimite) + ".";
        upsert(personal.getUsuario(), "Alunos aguardando aprovação", mensagem, TipoNotificacao.ALUNOS_PENDENTES, personal.getId());
    }

    @Transactional
    public int marcarTodasComoLidas(Usuario usuario) {
        return notificacaoRepository.marcarTodasComoLidas(usuario.getId());
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(Usuario usuario) {
        return notificacaoRepository.countByUsuarioIdAndLidaFalse(usuario.getId());
    }

    @Transactional(readOnly = true)
    public Page<NotificacaoResponseDTO> listarPorUsuario(Usuario usuario, Pageable pageable) {
        return notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuario.getId(), pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponseDTO> listarPorUsuario(Usuario usuario) {
        return notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuario.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public NotificacaoResponseDTO marcarComoLida(Usuario usuario, UUID notificacaoId) {
        Notificacao notif = notificacaoRepository.findById(notificacaoId)
                .orElseThrow(() -> new NotFoundException("Notificação não encontrada."));

        if (!notif.getUsuario().getId().equals(usuario.getId())) {
            throw new NotFoundException("Notificação não encontrada.");
        }

        notif.setLida(true);
        notif = notificacaoRepository.save(notif);
        return toDTO(notif);
    }

    public NotificacaoResponseDTO toDTO(Notificacao n) {
        return new NotificacaoResponseDTO(
                n.getId(),
                n.getTitulo(),
                n.getMensagem(),
                n.getTipo() != null ? n.getTipo().name() : null,
                n.getLida(),
                n.getReferenciaId(),
                n.getDataCriacao()
        );
    }

    public static String formatarDuracao(Duration duracao) {
        if (duracao.toDays() >= 1 && duracao.toHoursPart() == 0) {
            long dias = duracao.toDays();
            return dias + (dias == 1 ? " dia" : " dias");
        }
        if (duracao.toHours() >= 1) {
            long horas = duracao.toHours();
            return horas + (horas == 1 ? " hora" : " horas");
        }
        long minutos = Math.max(1, duracao.toMinutes());
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }

    @Transactional
    public Notificacao criarNotificacao(Usuario destinatario, String titulo, String mensagem, TipoNotificacao tipo, UUID referenciaId) {
        return salvar(destinatario, titulo, mensagem, tipo, referenciaId);
    }

    private Notificacao salvar(Usuario destinatario, String titulo, String mensagem, TipoNotificacao tipo, UUID referenciaId) {
        return notificacaoRepository.save(Notificacao.builder()
                .usuario(destinatario)
                .titulo(titulo)
                .mensagem(mensagem)
                .tipo(tipo)
                .referenciaId(referenciaId)
                .build());
    }

    private Notificacao upsert(Usuario destinatario, String titulo, String mensagem, TipoNotificacao tipo, UUID referenciaId) {
        Notificacao notificacao = notificacaoRepository
                .findByUsuarioIdAndTipoAndReferenciaId(destinatario.getId(), tipo, referenciaId)
                .orElseGet(() -> Notificacao.builder()
                        .usuario(destinatario)
                        .tipo(tipo)
                        .referenciaId(referenciaId)
                        .build());

        notificacao.setTitulo(titulo);
        notificacao.setMensagem(mensagem);
        notificacao.setLida(false);
        notificacao.setDataCriacao(OffsetDateTime.now());
        return notificacaoRepository.save(notificacao);
    }
}
