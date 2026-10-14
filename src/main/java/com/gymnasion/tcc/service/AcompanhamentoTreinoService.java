package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.*;
import com.gymnasion.tcc.domain.enums.TipoNotificacao;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@AllArgsConstructor
public class AcompanhamentoTreinoService {

    private final PersonalTrainerRepository personalTrainerRepository;
    private final AlunoRepository alunoRepository;
    private final RotinaTreinoRepository rotinaTreinoRepository;
    private final SessaoTreinoRepository sessaoTreinoRepository;
    private final NotificacaoRepository notificacaoRepository;

    public static final TipoNotificacao TIPO_NOTIFICACAO_PENDENCIAS = TipoNotificacao.METRICAS_PENDENTES;

    @Transactional
    public AcompanhamentoAlunoResponseDTO obterAcompanhamentoAluno(Usuario usuario, UUID alunoId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

        // Busca a rotina ativa do aluno
        List<RotinaTreino> rotinasAtivas = rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personal.getId(), aluno.getId())
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getAtivo()))
                .toList();

        RotinaTreino rotinaAtiva = rotinasAtivas.isEmpty() ? null : rotinasAtivas.get(0);

        List<SessaoTreino> sessoes = rotinaAtiva != null
                ? sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaAtiva.getId())
                : List.of();

        List<MetricaPendenteDTO> todasMetricasPendentes = new ArrayList<>();
        List<SessaoAcompanhamentoDTO> sessoesDTO = new ArrayList<>();

        int totalPlanejadas = 0;
        int totalConcluidas = 0;

        for (SessaoTreino sessao : sessoes) {
            if (sessao.getStatus() == StatusSessaoTreino.CONCLUIDA) {
                totalConcluidas++;
            } else {
                totalPlanejadas++;
            }

            List<MetricaPendenteDTO> pendenciasSessao = extrairPendenciasSessao(sessao);
            todasMetricasPendentes.addAll(pendenciasSessao);

            DesempenhoResponseDTO desempenhoDTO = toDesempenhoResponseDTO(sessao.getDesempenho());

            // US-24 C10: Sessão concluída com pendências do personal continua "concluída" e indica pendências separadamente
            sessoesDTO.add(new SessaoAcompanhamentoDTO(
                    sessao.getId(),
                    sessao.getSemana(),
                    sessao.getDataPlanejada(),
                    sessao.getDataReal(),
                    sessao.getDuracao(),
                    sessao.getAtividades(),
                    sessao.getStatus(),
                    sessao.getConcluidaAluno(),
                    sessao.getValidadaPersonal(),
                    !pendenciasSessao.isEmpty(),
                    pendenciasSessao.size(),
                    pendenciasSessao,
                    desempenhoDTO
            ));
        }

        // US-24 C4/C5/C6: Sincroniza notificação usando a mesma fonte de dados do indicador
        sincronizarNotificacaoAluno(personal, aluno, todasMetricasPendentes.size());

        String nomeAluno = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : null;
        String emailAluno = aluno.getUsuario() != null ? aluno.getUsuario().getEmail() : null;

        return new AcompanhamentoAlunoResponseDTO(
                aluno.getId(),
                nomeAluno,
                emailAluno,
                rotinaAtiva != null ? rotinaAtiva.getId() : null,
                rotinaAtiva != null ? rotinaAtiva.getTitulo() : null,
                rotinaAtiva != null ? rotinaAtiva.getModalidade().getId() : null,
                rotinaAtiva != null ? rotinaAtiva.getModalidade().getNome() : null,
                todasMetricasPendentes.size(),
                totalPlanejadas,
                totalConcluidas,
                todasMetricasPendentes,
                sessoesDTO
        );
    }

    @Transactional
    public ResumoPendenciasResponseDTO obterResumoPendencias(Usuario usuario) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        List<Aluno> alunos = alunoRepository.findByPersonalId(personal.getId());

        List<ResumoPendenciasResponseDTO.AlunoPendenciaItemDTO> itens = new ArrayList<>();
        int totalGeralPendencias = 0;

        for (Aluno aluno : alunos) {
            List<RotinaTreino> rotinasAtivas = rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personal.getId(), aluno.getId())
                    .stream()
                    .filter(r -> Boolean.TRUE.equals(r.getAtivo()))
                    .toList();

            if (rotinasAtivas.isEmpty()) {
                sincronizarNotificacaoAluno(personal, aluno, 0);
                continue;
            }

            RotinaTreino rotina = rotinasAtivas.get(0);
            List<SessaoTreino> sessoes = sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotina.getId());

            int pendenciasAluno = 0;
            for (SessaoTreino sessao : sessoes) {
                pendenciasAluno += extrairPendenciasSessao(sessao).size();
            }

            // US-24 C4/C5/C6: Atualiza ou remove notificação do aluno
            sincronizarNotificacaoAluno(personal, aluno, pendenciasAluno);

            if (pendenciasAluno > 0) {
                totalGeralPendencias += pendenciasAluno;
                String nome = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : "Aluno";
                itens.add(new ResumoPendenciasResponseDTO.AlunoPendenciaItemDTO(
                        aluno.getId(),
                        nome,
                        rotina.getId(),
                        rotina.getTitulo(),
                        pendenciasAluno
                ));
            }
        }

        return new ResumoPendenciasResponseDTO(
                itens.size(),
                totalGeralPendencias,
                itens
        );
    }

    @Transactional
    public List<NotificacaoResponseDTO> listarNotificacoes(Usuario usuario) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        // Atualiza pendências para garantir consistência
        obterResumoPendencias(usuario);

        return notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(personal.getUsuario().getId())
                .stream()
                .map(this::toNotificacaoResponseDTO)
                .toList();
    }

    @Transactional
    public NotificacaoResponseDTO marcarComoLida(Usuario usuario, UUID notificacaoId) {
        Notificacao notificacao = notificacaoRepository.findById(notificacaoId)
                .orElseThrow(() -> new NotFoundException("Notificação não encontrada."));

        if (!notificacao.getUsuario().getId().equals(usuario.getId())) {
            throw new NotFoundException("Notificação não encontrada.");
        }

        // US-24 C7: Apenas visualizar a notificação não resolve a pendência
        notificacao.setLida(true);
        notificacao = notificacaoRepository.save(notificacao);
        return toNotificacaoResponseDTO(notificacao);
    }

    @Transactional
    public void sincronizarNotificacaoParaAluno(Aluno aluno) {
        if (aluno == null || aluno.getId() == null) {
            return;
        }

        Aluno alunoCompleto = alunoRepository.findById(aluno.getId()).orElse(aluno);
        PersonalTrainer personal = alunoCompleto.getPersonal();
        if (personal == null || personal.getUsuario() == null) {
            return;
        }

        List<RotinaTreino> rotinasAtivas = rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personal.getId(), alunoCompleto.getId())
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getAtivo()))
                .toList();

        int pendencias = 0;
        if (!rotinasAtivas.isEmpty()) {
            RotinaTreino rotina = rotinasAtivas.get(0);
            List<SessaoTreino> sessoes = sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotina.getId());
            for (SessaoTreino sessao : sessoes) {
                pendencias += extrairPendenciasSessao(sessao).size();
            }
        }

        sincronizarNotificacaoAluno(personal, alunoCompleto, pendencias);
    }


    /**
     * US-24 C4, C5, C6: Criação, atualização ou remoção da notificação agrupada por aluno.
     * C6: Zerou pendências -> notificação removida.
     */
    private void sincronizarNotificacaoAluno(PersonalTrainer personal, Aluno aluno, int quantidadePendencias) {
        UUID usuarioPersonalId = personal.getUsuario().getId();
        Optional<Notificacao> notifOpt = notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(
                usuarioPersonalId, TIPO_NOTIFICACAO_PENDENCIAS, aluno.getId()
        );

        String nomeAluno = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : "Aluno";

        if (quantidadePendencias > 0) {
            String titulo = "Pendências de Métricas - " + nomeAluno;
            String mensagem = "O aluno " + nomeAluno + " possui " + quantidadePendencias + " métrica(s) pendente(s) de preenchimento.";

            if (notifOpt.isPresent()) {
                Notificacao notif = notifOpt.get();
                notif.setTitulo(titulo);
                notif.setMensagem(mensagem);
                notif.setDataCriacao(OffsetDateTime.now());
                notificacaoRepository.save(notif);
            } else {
                Notificacao nova = Notificacao.builder()
                        .usuario(personal.getUsuario())
                        .titulo(titulo)
                        .mensagem(mensagem)
                        .tipo(TIPO_NOTIFICACAO_PENDENCIAS)
                        .lida(false)
                        .referenciaId(aluno.getId())
                        .dataCriacao(OffsetDateTime.now())
                        .build();
                notificacaoRepository.save(nova);
            }
        } else {
            // US-24 C6: Zerou pendências -> notificação removida
            notifOpt.ifPresent(notificacaoRepository::delete);
        }
    }

    /**
     * Extrai as métricas pendentes do personal em uma sessão específica.
     * Métricas exclusivas do personal (PERSONAL) ou de ambos (AMBOS) que ainda não foram preenchidas no desempenho.
     */
    private List<MetricaPendenteDTO> extrairPendenciasSessao(SessaoTreino sessao) {
        if (sessao.getRotinaTreino() == null || sessao.getRotinaTreino().getMetricas() == null) {
            return List.of();
        }

        Set<Metrica> metricasRotina = sessao.getRotinaTreino().getMetricas();
        Desempenho desempenho = sessao.getDesempenho();

        Set<UUID> metricasPreenchidas = new HashSet<>();
        if (desempenho != null && desempenho.getRegistrosMetricas() != null) {
            for (RegistroMetrica rm : desempenho.getRegistrosMetricas()) {
                if (rm.getMetrica() != null && rm.getValor() != null && !rm.getValor().isBlank()) {
                    metricasPreenchidas.add(rm.getMetrica().getId());
                }
            }
        }

        List<MetricaPendenteDTO> pendentes = new ArrayList<>();
        for (Metrica m : metricasRotina) {
            // US-18 C5 / US-22 C5: Métricas exclusivas do personal
            boolean pendenciaPersonal = m.getQuemPreenche() == ResponsavelPreenchimento.PERSONAL
                    || (m.getQuemPreenche() == ResponsavelPreenchimento.AMBOS && Boolean.TRUE.equals(m.getObrigatoria()));

            if (pendenciaPersonal && !metricasPreenchidas.contains(m.getId())) {
                pendentes.add(new MetricaPendenteDTO(
                        sessao.getId(),
                        sessao.getSemana(),
                        sessao.getDataPlanejada(),
                        sessao.getDataReal(),
                        sessao.getStatus(),
                        sessao.getConcluidaAluno(),
                        m.getId(),
                        m.getTitulo(),
                        m.getTipo(),
                        m.getQuemPreenche(),
                        m.getObrigatoria()
                ));
            }
        }

        return pendentes;
    }

    private DesempenhoResponseDTO toDesempenhoResponseDTO(Desempenho d) {
        if (d == null) {
            return null;
        }

        List<RegistroMetricaResponseDTO> metricasDTO = d.getRegistrosMetricas() != null ? d.getRegistrosMetricas().stream()
                .map(rm -> new RegistroMetricaResponseDTO(
                        rm.getId(),
                        rm.getMetrica().getId(),
                        rm.getMetrica().getTitulo(),
                        rm.getMetrica().getTipo(),
                        rm.getValor()
                ))
                .toList() : List.of();

        return new DesempenhoResponseDTO(
                d.getId(),
                d.getDataRegistro(),
                d.getNivelCansaco(),
                d.getRegistradoPor(),
                d.getAluno() != null ? d.getAluno().getId() : null,
                d.getAluno() != null && d.getAluno().getUsuario() != null ? d.getAluno().getUsuario().getNome() : null,
                metricasDTO
        );
    }

    private NotificacaoResponseDTO toNotificacaoResponseDTO(Notificacao n) {
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

    private PersonalTrainer obterPersonalLogado(Usuario usuario) {
        return personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));
    }
}
