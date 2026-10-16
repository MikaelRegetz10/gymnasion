package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.CampoAuditado;
import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoOperacaoAuditoria;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.MetricasInvalidasException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RotinaTreinoService {

    private final RotinaTreinoRepository rotinaTreinoRepository;
    private final ModalidadeRepository modalidadeRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final AlunoRepository alunoRepository;
    private final MetricaRepository metricaRepository;
    private final SessaoTreinoRepository sessaoTreinoRepository;
    private final DesempenhoRepository desempenhoRepository;
    private final AcompanhamentoTreinoService acompanhamentoTreinoService;
    private final NotificacaoService notificacaoService;
    private final AuditoriaDesempenhoRepository auditoriaDesempenhoRepository;
    private final TurmaRepository turmaRepository;

    @Transactional
    public RotinaTreinoResponseDTO criarRotinaTreino(Usuario usuario, CriarRotinaTreinoRequestDTO dto) {
        PersonalTrainer personal = obterPersonalLogado(usuario);

        Modalidade modalidade = modalidadeRepository.findById(dto.modalidadeId())
                .orElseThrow(() -> new NotFoundException("Modalidade não encontrada."));

        // US-10 Cenário 2: Verifica se existem métricas cadastradas para a modalidade antes de continuar
        List<Metrica> metricasDaModalidade = metricaRepository.findByPersonalTrainerIdAndModalidadeId(
                personal.getId(), dto.modalidadeId()
        );
        if (metricasDaModalidade.isEmpty()) {
            throw new BusinessException("Nenhuma métrica cadastrada para essa modalidade. Cadastre ao menos uma métrica antes de continuar.");
        }

        // Validação e associação das métricas selecionadas
        Set<Metrica> metricasValidadas = new HashSet<>();
        for (UUID metricaId : dto.metricasIds()) {
            Metrica metrica = metricaRepository.findByIdAndPersonalTrainerId(metricaId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Métrica com ID " + metricaId + " não foi encontrada para este Personal Trainer."));

            if (!metrica.getModalidade().getId().equals(modalidade.getId())) {
                throw new BusinessException("A métrica '" + metrica.getTitulo() + "' não pertence à modalidade selecionada.");
            }
            if (Boolean.FALSE.equals(metrica.getAtivo())) {
                throw new BusinessException("A métrica '" + metrica.getTitulo() + "' está descontinuada e não pode ser vinculada a uma nova rotina.");
            }
            metricasValidadas.add(metrica);
        }

        Set<Aluno> alunosValidados = new HashSet<>();
        for (UUID alunoId : dto.alunosIds()) {
            Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

            if (aluno.getStatus() == StatusConvite.INATIVO) {
                throw new BusinessException("Erro. Este aluno está inativo.");
            }

            // Trava de 1 rotina ativa (US-11 / US-25 C6)
            if (rotinaTreinoRepository.existsByAlunoIdAndAtivoTrue(aluno.getId())) {
                String nome = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : "selecionado";
                throw new BusinessException("O aluno " + nome + " já possui uma rotina de treino ativa. A rotina atual precisa ser encerrada antes.");
            }

            alunosValidados.add(aluno);
        }


        RotinaTreino rotina = RotinaTreino.builder()
                .titulo(dto.titulo().trim())
                .modalidade(modalidade)
                .frequenciaSemanal(dto.frequenciaSemanal())
                .objetivos(dto.objetivos())
                .personalTrainer(personal)
                .alunos(alunosValidados)
                .metricas(metricasValidadas)
                .build();

        rotina = rotinaTreinoRepository.save(rotina);
        return toResponseDTO(rotina);
    }

    @Transactional(readOnly = true)
    public Page<RotinaTreinoResponseDTO> listarPorPersonal(Usuario usuario, Pageable pageable) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        return rotinaTreinoRepository.findByPersonalTrainerId(personal.getId(), pageable)
                .map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<RotinaTreinoResponseDTO> listarPorPersonal(Usuario usuario) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        return rotinaTreinoRepository.findByPersonalTrainerId(personal.getId())
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<RotinaTreinoResponseDTO> listarPorAluno(Usuario usuario, UUID alunoId, Pageable pageable) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        return rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personal.getId(), alunoId, pageable)
                .map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<RotinaTreinoResponseDTO> listarPorAluno(Usuario usuario, UUID alunoId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        return rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personal.getId(), alunoId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<RotinaTreinoResponseDTO> listarMinhasRotinas(Usuario usuario, Pageable pageable) {
        Aluno aluno = alunoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));
        return rotinaTreinoRepository.findByAlunosId(aluno.getId(), pageable)
                .map(this::toResponseDTO);
    }


    @Transactional(readOnly = true)
    public RotinaTreinoResponseDTO buscarPorId(Usuario usuario, UUID rotinaId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        RotinaTreino rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));
        return toResponseDTO(rotina);
    }

    @Transactional
    public MensagemResponseDTO excluirRotina(Usuario usuario, UUID rotinaId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        RotinaTreino rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));

        // US-12 / Proteção de histórico: impede a exclusão física se houver sessões já executadas
        List<SessaoTreino> sessoes = sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaId);
        boolean possuiSessoesExecutadas = sessoes.stream().anyMatch(this::possuiHistoricoExecucao);

        if (possuiSessoesExecutadas) {
            throw new BusinessException("Não é possível excluir uma rotina de treino que já possui sessões executadas. Para finalizar a rotina preservando o histórico, utilize o encerramento.");
        }

        List<Aluno> alunosVinculados = rotina.getAlunos() != null ? new ArrayList<>(rotina.getAlunos()) : List.of();

        rotinaTreinoRepository.delete(rotina);

        // Atualiza notificações caso houvesse pendências atreladas à rotina removida
        if (acompanhamentoTreinoService != null) {
            for (Aluno a : alunosVinculados) {
                acompanhamentoTreinoService.sincronizarNotificacaoParaAluno(a);
            }
        }

        return new MensagemResponseDTO("Rotina excluída com sucesso!");
    }

    private boolean possuiHistoricoExecucao(SessaoTreino sessao) {
        if (sessao == null) return false;
        return sessao.getStatus() == StatusSessaoTreino.CONCLUIDA
                || Boolean.TRUE.equals(sessao.getConcluidaAluno())
                || Boolean.TRUE.equals(sessao.getValidadaPersonal())
                || sessao.getDataReal() != null
                || sessao.getDesempenho() != null;
    }

    // =========================================================================
    // US-11: Vinculação de treino ao aluno
    // =========================================================================

    @Transactional
    public RotinaTreinoResponseDTO vincularAlunos(Usuario usuario, UUID rotinaId, VincularAlunosRotinaRequestDTO dto) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        RotinaTreino rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));

        if (Boolean.FALSE.equals(rotina.getAtivo())) {
            throw new BusinessException("Não é possível vincular alunos a uma rotina de treino encerrada/inativa.");
        }

        // US-11 Cenário 3: Nenhum aluno selecionado
        if (dto == null || dto.alunosIds() == null || dto.alunosIds().isEmpty()) {
            throw new BusinessException("É necessário selecionar pelo menos 1 (um) aluno.");
        }

        for (UUID alunoId : dto.alunosIds()) {
            Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

            // RN-014: Aluno inativo
            if (aluno.getStatus() == StatusConvite.INATIVO) {
                throw new BusinessException("Erro. Este aluno está inativo.");
            }

            // US-11 Cenário 2: Aluno já possui este treino vinculado
            boolean jaVinculado = rotina.getAlunos().stream().anyMatch(a -> a.getId().equals(aluno.getId()));
            if (jaVinculado) {
                throw new BusinessException("Esse aluno já possui o treino vinculado.");
            }

            // Trava de 1 rotina ativa (US-11 / US-25 C6)
            if (rotinaTreinoRepository.existsByAlunoIdAndAtivoTrueAndIdNot(aluno.getId(), rotina.getId())) {
                String nome = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : "selecionado";
                throw new BusinessException("O aluno " + nome + " já possui uma rotina de treino ativa. A rotina atual precisa ser encerrada antes.");
            }

            rotina.getAlunos().add(aluno);
        }

        rotina = rotinaTreinoRepository.save(rotina);

        // US-24 C4: Notificação sincronizada ao vincular alunos
        if (acompanhamentoTreinoService != null && rotina.getAlunos() != null) {
            for (Aluno a : rotina.getAlunos()) {
                acompanhamentoTreinoService.sincronizarNotificacaoParaAluno(a);
            }
        }

        return toResponseDTO(rotina);
    }

    @Transactional
    public RotinaTreinoResponseDTO vincularTurma(Usuario usuario, UUID rotinaId, UUID turmaId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        RotinaTreino rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));

        if (Boolean.FALSE.equals(rotina.getAtivo())) {
            throw new BusinessException("Não é possível vincular alunos a uma rotina de treino encerrada/inativa.");
        }

        Turma turma = turmaRepository.findByIdAndPersonalId(turmaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Turma não encontrada."));

        if (rotina.getModalidade() == null || turma.getModalidade() == null
                || !rotina.getModalidade().getId().equals(turma.getModalidade().getId())) {
            throw new BusinessException("A modalidade da turma deve ser a mesma da rotina de treino.");
        }

        if (turma.getAlunos() == null || turma.getAlunos().isEmpty()) {
            throw new BusinessException("É necessário selecionar pelo menos 1 (um) aluno.");
        }

        Set<Aluno> novosAlunos = new HashSet<>();
        for (Aluno aluno : turma.getAlunos()) {
            // RN-014: Aluno inativo
            if (aluno.getStatus() == StatusConvite.INATIVO) {
                throw new BusinessException("Erro. Este aluno está inativo.");
            }

            // Ignora alunos da turma que já estão vinculados a esta mesma rotina (a menos que todos já estejam)
            boolean jaVinculado = rotina.getAlunos().stream().anyMatch(a -> a.getId().equals(aluno.getId()));
            if (jaVinculado) {
                continue;
            }

            // Trava de 1 rotina ativa (US-11 / US-25 C6)
            if (rotinaTreinoRepository.existsByAlunoIdAndAtivoTrueAndIdNot(aluno.getId(), rotina.getId())) {
                String nome = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : "selecionado";
                throw new BusinessException("O aluno " + nome + " já possui uma rotina de treino ativa. A rotina atual precisa ser encerrada antes.");
            }

            novosAlunos.add(aluno);
        }

        if (novosAlunos.isEmpty()) {
            throw new BusinessException("Esse aluno já possui o treino vinculado.");
        }

        rotina.getAlunos().addAll(novosAlunos);
        rotina = rotinaTreinoRepository.save(rotina);

        // US-24 C4: Notificação sincronizada ao vincular alunos
        if (acompanhamentoTreinoService != null && rotina.getAlunos() != null) {
            for (Aluno a : rotina.getAlunos()) {
                acompanhamentoTreinoService.sincronizarNotificacaoParaAluno(a);
            }
        }

        return toResponseDTO(rotina);
    }

    @Transactional
    public RotinaTreinoResponseDTO desvincularAluno(Usuario usuario, UUID rotinaId, UUID alunoId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        RotinaTreino rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));

        Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

        if (Boolean.FALSE.equals(rotina.getAtivo())) {
            throw new BusinessException("Não é possível desvincular alunos de uma rotina de treino já encerrada. O histórico de treinos do aluno deve ser preservado.");
        }

        boolean vinculado = rotina.getAlunos().removeIf(a -> a.getId().equals(aluno.getId()));
        if (!vinculado) {
            throw new BusinessException("O aluno não está vinculado a esta rotina.");
        }

        // RN-012: Toda rotina vinculada a >= 1 aluno; proibido salvar sem vínculo
        if (rotina.getAlunos().isEmpty()) {
            throw new BusinessException("A rotina deve possuir pelo menos um aluno vinculado. Para remover todos os alunos, exclua a rotina.");
        }

        rotina = rotinaTreinoRepository.save(rotina);

        // US-24 C6: Atualiza notificação ao remover aluno
        if (acompanhamentoTreinoService != null) {
            acompanhamentoTreinoService.sincronizarNotificacaoParaAluno(aluno);
        }

        return toResponseDTO(rotina);
    }

    @Transactional
    public RotinaTreinoResponseDTO encerrarRotina(Usuario usuario, UUID rotinaId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        RotinaTreino rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));

        if (Boolean.FALSE.equals(rotina.getAtivo())) {
            throw new BusinessException("Esta rotina de treino já está encerrada.");
        }

        // US-25 C7: Encerrar remove sessões futuras não realizadas; preserva as realizadas e resultados
        if (rotina.getSessoes() != null) {
            rotina.getSessoes().removeIf(s -> s.getStatus() == StatusSessaoTreino.PLANEJADA);
        }

        rotina.setAtivo(false);
        rotina = rotinaTreinoRepository.save(rotina);

        // US-24 C6: Atualiza notificações ao encerrar rotina (remove pendências de sessões que foram removidas)
        if (acompanhamentoTreinoService != null && rotina.getAlunos() != null) {
            for (Aluno a : rotina.getAlunos()) {
                acompanhamentoTreinoService.sincronizarNotificacaoParaAluno(a);
            }
        }

        return toResponseDTO(rotina);
    }

    // =========================================================================
    // US-25: Reutilizar e atribuir rotinas / encerrar rotina
    // =========================================================================

    @Transactional
    public RotinaTreinoResponseDTO reutilizarRotina(Usuario usuario, UUID rotinaOrigemId, ReutilizarRotinaRequestDTO dto) {
        return reutilizarRotinaInterno(usuario, rotinaOrigemId, dto.alunoId(), dto.dataInicio(), dto.titulo(), dto.objetivos());
    }

    @Transactional
    public RotinaTreinoResponseDTO atribuirRotina(Usuario usuario, UUID alunoId, AtribuirRotinaAlunoRequestDTO dto) {
        return reutilizarRotinaInterno(usuario, dto.rotinaOrigemId(), alunoId, dto.dataInicio(), dto.titulo(), dto.objetivos());
    }

    private RotinaTreinoResponseDTO reutilizarRotinaInterno(
            Usuario usuario,
            UUID rotinaOrigemId,
            UUID alunoId,
            LocalDate dataInicio,
            String tituloPersonalizado,
            String objetivosPersonalizados
    ) {
        PersonalTrainer personal = obterPersonalLogado(usuario);

        RotinaTreino rotinaOrigem = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaOrigemId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));

        Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

        // RN-014: Aluno inativo
        if (aluno.getStatus() == StatusConvite.INATIVO) {
            throw new BusinessException("Erro. Este aluno está inativo.");
        }

        // US-25 C6: Máximo de 1 rotina ativa por aluno
        if (rotinaTreinoRepository.existsByAlunoIdAndAtivoTrue(aluno.getId())) {
            String nome = aluno.getUsuario() != null ? aluno.getUsuario().getNome() : "selecionado";
            throw new BusinessException("O aluno " + nome + " já possui uma rotina de treino ativa. A rotina atual precisa ser encerrada antes.");
        }

        String titulo = (tituloPersonalizado != null && !tituloPersonalizado.trim().isEmpty())
                ? tituloPersonalizado.trim()
                : rotinaOrigem.getTitulo();

        String objetivos = objetivosPersonalizados != null ? objetivosPersonalizados : rotinaOrigem.getObjetivos();

        // US-25 C3 / C5 / C9: Nova rotina como cópia independente, vinculada ao aluno, ativa
        RotinaTreino novaRotina = RotinaTreino.builder()
                .titulo(titulo)
                .modalidade(rotinaOrigem.getModalidade())
                .frequenciaSemanal(rotinaOrigem.getFrequenciaSemanal())
                .objetivos(objetivos)
                .personalTrainer(personal)
                .ativo(true)
                .alunos(new HashSet<>(Set.of(aluno)))
                .metricas(new HashSet<>(rotinaOrigem.getMetricas()))
                .sessoes(new ArrayList<>())
                .build();

        // US-25 C3 e C4: Copia estrutura das sessões e recalcula datas planejadas conforme nova data de início
        List<SessaoTreino> sessoesOrigem = (rotinaOrigem.getSessoes() != null && !rotinaOrigem.getSessoes().isEmpty())
                ? rotinaOrigem.getSessoes()
                : sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaOrigem.getId());

        if (sessoesOrigem != null && !sessoesOrigem.isEmpty()) {
            List<SessaoTreino> sessoesOrdenadas = sessoesOrigem.stream()
                    .sorted(Comparator.comparing(SessaoTreino::getSemana, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(SessaoTreino::getDataPlanejada, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            LocalDate dataMinimaOrigem = sessoesOrdenadas.stream()
                    .map(SessaoTreino::getDataPlanejada)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(null);

            for (SessaoTreino so : sessoesOrdenadas) {
                long offsetDias;
                if (dataMinimaOrigem != null && so.getDataPlanejada() != null) {
                    offsetDias = ChronoUnit.DAYS.between(dataMinimaOrigem, so.getDataPlanejada());
                } else {
                    int semanaNum = (so.getSemana() != null && so.getSemana() > 0) ? so.getSemana() - 1 : 0;
                    offsetDias = semanaNum * 7L;
                }

                LocalDate novaDataPlanejada = dataInicio.plusDays(offsetDias);

                // C3 e C9: Status PLANEJADA, sem resultados, datas reais ou desempenho da origem
                SessaoTreino novaSessao = SessaoTreino.builder()
                        .rotinaTreino(novaRotina)
                        .semana(so.getSemana())
                        .dataPlanejada(novaDataPlanejada)
                        .duracao(so.getDuracao())
                        .atividades(so.getAtividades())
                        .status(StatusSessaoTreino.PLANEJADA)
                        .concluidaAluno(false)
                        .validadaPersonal(false)
                        .dataReal(null)
                        .desempenho(null)
                        .build();

                novaRotina.getSessoes().add(novaSessao);
            }
        }

        novaRotina = rotinaTreinoRepository.save(novaRotina);

        // US-24: Sincroniza notificações para o aluno
        if (acompanhamentoTreinoService != null) {
            acompanhamentoTreinoService.sincronizarNotificacaoParaAluno(aluno);
        }

        return toResponseDTO(novaRotina);
    }

    @Transactional
    public SessaoTreinoResponseDTO adicionarSessao(Usuario usuario, UUID rotinaId, CriarSessaoTreinoRequestDTO dto) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        RotinaTreino rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));

        SessaoTreino sessao = SessaoTreino.builder()
                .rotinaTreino(rotina)
                .semana(dto.semana())
                .dataPlanejada(dto.dataPlanejada())
                .duracao(dto.duracao())
                .atividades(dto.atividades())
                .status(StatusSessaoTreino.PLANEJADA)
                .concluidaAluno(false)
                .validadaPersonal(false)
                .build();

        sessao = sessaoTreinoRepository.save(sessao);

        // US-24 C4: Notificação criada/atualizada assim que houver pendência do personal
        if (acompanhamentoTreinoService != null && rotina.getAlunos() != null) {
            for (Aluno a : rotina.getAlunos()) {
                acompanhamentoTreinoService.sincronizarNotificacaoParaAluno(a);
            }
        }

        return toSessaoResponseDTO(sessao);
    }


    @Transactional(readOnly = true)
    public PlanejamentoRotinaResponseDTO consultarPlanejamento(Usuario usuario, UUID rotinaId) {
        return consultarPlanejamento(usuario, rotinaId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public PlanejamentoRotinaResponseDTO consultarPlanejamento(Usuario usuario, UUID rotinaId, LocalDate dataReferencia) {
        RotinaTreino rotina;
        if (usuario.getRole() == Role.PERSONAL_TRAINER) {
            PersonalTrainer personal = obterPersonalLogado(usuario);
            rotina = rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));
        } else {
            Aluno aluno = alunoRepository.findByUsuario(usuario)
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));
            rotina = rotinaTreinoRepository.findById(rotinaId)
                    .filter(r -> r.getAlunos().contains(aluno))
                    .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada para este aluno."));
        }

        List<SessaoTreino> sessoes = sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaId);

        // US-21 C2: Aluno visualizando rotina ativa tem visualização restrita à semana atual e à semana anterior;
        // semanas futuras NÃO são exibidas.
        // US-21 C5: Em rotinas encerradas, todas as semanas são exibidas para consulta do histórico.
        // US-10 C1: O personal trainer consulta o planejamento completo com todas as semanas.
        if (usuario.getRole() == Role.ALUNO && Boolean.TRUE.equals(rotina.getAtivo())) {
            LocalDate ref = dataReferencia != null ? dataReferencia : LocalDate.now();
            int semanaAtual = calcularSemanaAtual(sessoes, ref);
            int semanaAnterior = semanaAtual - 1;

            sessoes = sessoes.stream()
                    .filter(s -> s.getSemana() != null && (s.getSemana() == semanaAtual || s.getSemana() == semanaAnterior))
                    .toList();
        }

        Map<Integer, List<SessaoTreinoResponseDTO>> sessoesPorSemana = sessoes.stream()
                .map(this::toSessaoResponseDTO)
                .collect(Collectors.groupingBy(SessaoTreinoResponseDTO::semana, TreeMap::new, Collectors.toList()));

        List<PlanejamentoRotinaResponseDTO.SemanaPlanejamentoDTO> semanasDTO = sessoesPorSemana.entrySet().stream()
                .map(entry -> new PlanejamentoRotinaResponseDTO.SemanaPlanejamentoDTO(entry.getKey(), entry.getValue()))
                .toList();

        return new PlanejamentoRotinaResponseDTO(
                rotina.getId(),
                rotina.getTitulo(),
                rotina.getModalidade().getId(),
                rotina.getModalidade().getNome(),
                rotina.getFrequenciaSemanal(),
                rotina.getObjetivos(),
                semanasDTO
        );
    }

    private int calcularSemanaAtual(List<SessaoTreino> sessoes, LocalDate dataReferencia) {
        if (sessoes == null || sessoes.isEmpty()) {
            return 1;
        }

        List<SessaoTreino> comData = sessoes.stream()
                .filter(s -> s.getDataPlanejada() != null && s.getSemana() != null)
                .sorted(Comparator.comparing(SessaoTreino::getDataPlanejada))
                .toList();

        if (comData.isEmpty()) {
            return sessoes.stream()
                    .map(SessaoTreino::getSemana)
                    .filter(Objects::nonNull)
                    .min(Integer::compareTo)
                    .orElse(1);
        }

        LocalDate primeiraData = comData.get(0).getDataPlanejada();
        if (dataReferencia.isBefore(primeiraData)) {
            return comData.get(0).getSemana();
        }

        LocalDate ultimaData = comData.get(comData.size() - 1).getDataPlanejada();
        if (dataReferencia.isAfter(ultimaData)) {
            return comData.get(comData.size() - 1).getSemana();
        }

        return comData.stream()
                .filter(s -> !s.getDataPlanejada().isAfter(dataReferencia))
                .map(SessaoTreino::getSemana)
                .reduce((first, second) -> second)
                .orElse(comData.get(0).getSemana());
    }

    @Transactional
    public SessaoTreinoResponseDTO editarSessaoFutura(Usuario usuario, UUID sessaoId, AtualizarSessaoTreinoRequestDTO dto) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        SessaoTreino sessao = sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Sessão de treino não encontrada para este Personal Trainer."));

        // US-10 Cenário 2: Editar sessões futuras - só sessões não executadas; preserva dados/resultados das executadas
        if (sessao.getStatus() == StatusSessaoTreino.CONCLUIDA) {
            throw new BusinessException("Não é possível editar uma sessão de treino já executada. Os dados históricos devem ser preservados.");
        }

        sessao.setSemana(dto.semana());
        sessao.setDataPlanejada(dto.dataPlanejada());
        sessao.setDuracao(dto.duracao());
        sessao.setAtividades(dto.atividades());

        sessao = sessaoTreinoRepository.save(sessao);
        return toSessaoResponseDTO(sessao);
    }

    @Transactional(readOnly = true)
    public FormularioExecucaoResponseDTO obterFormularioExecucao(Usuario usuario, UUID sessaoId) {
        Aluno aluno = alunoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));
        SessaoTreino sessao = sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())
                .orElseThrow(() -> new NotFoundException("Sessão de treino não encontrada para este aluno."));

        // US-22 Cenário 1: Disponibiliza as métricas configuradas pelo personal (descontinuadas não aparecem - US-19 C3)
        List<RotinaTreinoResponseDTO.MetricaResumoDTO> metricasDTO = sessao.getRotinaTreino().getMetricas().stream()
                .filter(metrica -> Boolean.TRUE.equals(metrica.getAtivo()))
                .sorted(Comparator.comparing(Metrica::getTitulo))
                .map(metrica -> new RotinaTreinoResponseDTO.MetricaResumoDTO(
                        metrica.getId(),
                        metrica.getTitulo(),
                        metrica.getTipo(),
                        metrica.getObrigatoria(),
                        metrica.getQuemPreenche()
                ))
                .toList();

        // US-22 Cenário 8: Sessão já confirmada é exibida apenas para consulta
        return new FormularioExecucaoResponseDTO(
                toSessaoResponseDTO(sessao),
                !execucaoConfirmada(sessao),
                metricasDTO
        );
    }

    @Transactional
    public SessaoTreinoResponseDTO registrarExecucao(Usuario usuario, UUID sessaoId, RegistrarExecucaoSessaoRequestDTO dto) {
        SessaoTreino sessao;
        Aluno alunoResponsavel;
        String registradoPor;
        List<RegistrarResultadoMetricaDTO> resultados = dto.resultados() != null ? dto.resultados() : List.of();

        if (usuario.getRole() == Role.PERSONAL_TRAINER) {
            PersonalTrainer personal = obterPersonalLogado(usuario);
            sessao = sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Sessão de treino não encontrada para este Personal Trainer."));

            if (dto.alunoId() != null) {
                alunoResponsavel = alunoRepository.findByIdAndPersonalId(dto.alunoId(), personal.getId())
                        .orElseThrow(() -> new NotFoundException("Aluno não encontrado para este Personal Trainer."));
            } else if (!sessao.getRotinaTreino().getAlunos().isEmpty()) {
                alunoResponsavel = sessao.getRotinaTreino().getAlunos().iterator().next();
            } else {
                throw new BusinessException("Nenhum aluno informado ou vinculado à rotina.");
            }

            // RN-014: Aluno inativo
            if (alunoResponsavel.getStatus() == StatusConvite.INATIVO) {
                throw new BusinessException("Erro. Este aluno está inativo.");
            }

            // US-10: O registro feito pelo personal exige ao menos um resultado de métrica
            if (resultados.isEmpty()) {
                throw new BusinessException("É necessário informar pelo menos um resultado de métrica.");
            }
            registradoPor = "PERSONAL_TRAINER";
        } else {
            alunoResponsavel = alunoRepository.findByUsuario(usuario)
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

            // RN-014: Aluno inativo
            if (alunoResponsavel.getStatus() == StatusConvite.INATIVO) {
                throw new BusinessException("Erro. Este aluno está inativo.");
            }

            sessao = sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, alunoResponsavel.getId())
                    .orElseThrow(() -> new NotFoundException("Sessão de treino não encontrada para este aluno."));

            // US-22 Cenário 8: Após a confirmação, o registro não pode ser alterado pelo aluno (correções via US-23)
            if (execucaoConfirmada(sessao)) {
                throw new BusinessException("A execução desta sessão já foi confirmada e não pode ser alterada. Eventuais correções devem ser realizadas pelo personal trainer.");
            }
            sessao.setConcluidaAluno(true);
            registradoPor = "ALUNO";
        }

        boolean isPersonal = usuario.getRole() == Role.PERSONAL_TRAINER;

        // US-23: Com a execução já registrada, o personal apenas inclui métricas pendentes (com auditoria);
        // data real, duração, RPE e valores existentes são preservados e só podem ser alterados pela correção (US-23)
        boolean somenteInclusaoPendentes = isPersonal && sessao.getDesempenho() != null;
        List<AuditoriaDesempenho> auditorias = new ArrayList<>();

        if (!somenteInclusaoPendentes) {
            // US-10 Cenário 4: Data diferente da planejada - registra data efetiva e preserva a planejada
            sessao.setDataReal(dto.dataReal());
            if (dto.duracao() != null) {
                sessao.setDuracao(dto.duracao());
            }

            // US-10 Cenário 5: Confirmar conclusão - sessão concluída, registro vai ao histórico do aluno
            sessao.setStatus(StatusSessaoTreino.CONCLUIDA);
        }

        // US-10 Cenário 3: Registrar resultados - armazena vínculo com aluno, rotina, sessão e métricas
        Desempenho desempenho = sessao.getDesempenho();
        if (somenteInclusaoPendentes) {
            if (desempenho.getAluno() != null) {
                alunoResponsavel = desempenho.getAluno();
            }
        } else if (desempenho == null) {
            desempenho = Desempenho.builder()
                    .sessaoTreino(sessao)
                    .aluno(alunoResponsavel)
                    .registradoPor(registradoPor)
                    .dataRegistro(OffsetDateTime.now())
                    .nivelCansaco(dto.nivelCansaco())
                    .build();
            sessao.setDesempenho(desempenho);
        } else {
            desempenho.setAluno(alunoResponsavel);
            desempenho.setRegistradoPor(registradoPor);
            if (dto.nivelCansaco() != null) {
                desempenho.setNivelCansaco(dto.nivelCansaco());
            }
        }

        Set<Metrica> metricasDaRotina = sessao.getRotinaTreino().getMetricas();
        Map<String, String> camposInvalidos = new LinkedHashMap<>();
        for (RegistrarResultadoMetricaDTO resDto : resultados) {
            Metrica metrica = metricaRepository.findById(resDto.metricaId())
                    .orElseThrow(() -> new NotFoundException("Métrica com ID " + resDto.metricaId() + " não encontrada."));

            boolean pertenceRotina = metricasDaRotina.stream().anyMatch(m -> m.getId().equals(metrica.getId()));
            if (!pertenceRotina) {
                throw new BusinessException("A métrica '" + metrica.getTitulo() + "' não está associada à rotina desta sessão.");
            }

            // US-18 C5 / US-10 C4: Valida permissão de preenchimento por papel (quemPreenche)
            ResponsavelPreenchimento quemPreenche = metrica.getQuemPreenche();
            if (isPersonal && quemPreenche == ResponsavelPreenchimento.ALUNO) {
                throw new BusinessException("A métrica '" + metrica.getTitulo() + "' é de preenchimento exclusivo do aluno.");
            }
            if (!isPersonal && quemPreenche == ResponsavelPreenchimento.PERSONAL) {
                throw new BusinessException("A métrica '" + metrica.getTitulo() + "' é de preenchimento exclusivo do personal trainer.");
            }

            // US-22 Cenário 9 / US-18 (RN-015): Valida o valor de acordo com o tipo da métrica
            String valor = resDto.valor().trim();
            String erroTipo = ValidadorValorMetrica.validar(metrica, valor);
            if (erroTipo != null) {
                camposInvalidos.put(metrica.getId().toString(), erroTipo);
                continue;
            }

            if (somenteInclusaoPendentes) {
                boolean jaPreenchida = desempenho.getRegistrosMetricas().stream()
                        .anyMatch(rmExistente -> rmExistente.getMetrica().getId().equals(metrica.getId()));
                if (jaPreenchida) {
                    throw new BusinessException("A métrica '" + metrica.getTitulo() + "' já possui valor registrado. Para alterá-la, utilize a correção do registro de desempenho.");
                }
                // US-23 Cenário 7: Preenchimento de métrica pendente é registrado como inclusão na auditoria
                auditorias.add(AuditoriaDesempenho.builder()
                        .desempenho(desempenho)
                        .tipoOperacao(TipoOperacaoAuditoria.INCLUSAO)
                        .campo(CampoAuditado.METRICA)
                        .metrica(metrica)
                        .valorNovo(valor)
                        .usuario(usuario)
                        .build());
            } else {
                // Atualiza métrica existente ou adiciona nova (preserva métricas preenchidas por outros papéis)
                desempenho.getRegistrosMetricas().removeIf(rmExistente -> rmExistente.getMetrica().getId().equals(metrica.getId()));
            }

            RegistroMetrica rm = RegistroMetrica.builder()
                    .desempenho(desempenho)
                    .metrica(metrica)
                    .valor(valor)
                    .build();

            desempenho.getRegistrosMetricas().add(rm);
        }

        // US-22 Cenários 3 e 4: Métricas obrigatórias do aluno devem ser preenchidas; opcionais e exclusivas do personal não bloqueiam
        if (!isPersonal) {
            Set<UUID> metricasInformadas = resultados.stream()
                    .map(RegistrarResultadoMetricaDTO::metricaId)
                    .collect(Collectors.toSet());
            metricasDaRotina.stream()
                    .filter(metrica -> Boolean.TRUE.equals(metrica.getAtivo()))
                    .filter(metrica -> Boolean.TRUE.equals(metrica.getObrigatoria()))
                    .filter(metrica -> metrica.getQuemPreenche() != ResponsavelPreenchimento.PERSONAL)
                    .filter(metrica -> !metricasInformadas.contains(metrica.getId()))
                    .sorted(Comparator.comparing(Metrica::getTitulo))
                    .forEach(metrica -> camposInvalidos.put(
                            metrica.getId().toString(),
                            "A métrica '" + metrica.getTitulo() + "' é obrigatória e deve ser preenchida."
                    ));
        }

        // US-22 Cenário 10: Impede a confirmação e identifica todos os campos que precisam ser corrigidos
        if (!camposInvalidos.isEmpty()) {
            throw new MetricasInvalidasException(camposInvalidos);
        }

        sessao = sessaoTreinoRepository.save(sessao);
        if (!auditorias.isEmpty()) {
            auditoriaDesempenhoRepository.saveAll(auditorias);
        }

        // US-24 C5 e C6: Preencher métricas atualiza contagem na notificação e no perfil (remove se zerar)
        if (acompanhamentoTreinoService != null) {
            acompanhamentoTreinoService.sincronizarNotificacaoParaAluno(alunoResponsavel);
        }

        // US-10 C5/C6: aluno concluiu o treino -> notifica o personal para acompanhar/validar
        if (!isPersonal && notificacaoService != null) {
            notificacaoService.notificarTreinoConcluido(alunoResponsavel, sessao);
        }

        return toSessaoResponseDTO(sessao);
    }


    @Transactional
    public SessaoTreinoResponseDTO validarExecucao(Usuario usuario, UUID sessaoId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        SessaoTreino sessao = sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Sessão de treino não encontrada para este Personal Trainer."));

        // US-10 Cenário 6: Validar execução - personal pode validar; conclusão do aluno permanece mesmo sem validação
        sessao.setValidadaPersonal(true);
        sessao = sessaoTreinoRepository.save(sessao);
        return toSessaoResponseDTO(sessao);
    }

    @Transactional(readOnly = true)
    public Page<SessaoTreinoResponseDTO> consultarHistoricoRotina(Usuario usuario, UUID rotinaId, Pageable pageable) {
        if (usuario.getRole() == Role.PERSONAL_TRAINER) {
            PersonalTrainer personal = obterPersonalLogado(usuario);
            rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));
        } else {
            Aluno aluno = alunoRepository.findByUsuario(usuario)
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));
            rotinaTreinoRepository.findById(rotinaId)
                    .filter(r -> r.getAlunos().contains(aluno))
                    .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada para este aluno."));
        }

        // US-10 Cenário 8: Histórico - treinos realizados e resultados
        return sessaoTreinoRepository.findByRotinaTreinoIdAndStatusOrderByDataRealDesc(rotinaId, StatusSessaoTreino.CONCLUIDA, pageable)
                .map(this::toSessaoResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<SessaoTreinoResponseDTO> consultarHistoricoRotina(Usuario usuario, UUID rotinaId) {
        if (usuario.getRole() == Role.PERSONAL_TRAINER) {
            PersonalTrainer personal = obterPersonalLogado(usuario);
            rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaId, personal.getId())
                    .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada."));
        } else {
            Aluno aluno = alunoRepository.findByUsuario(usuario)
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));
            rotinaTreinoRepository.findById(rotinaId)
                    .filter(r -> r.getAlunos().contains(aluno))
                    .orElseThrow(() -> new NotFoundException("Rotina de treino não encontrada para este aluno."));
        }

        // US-10 Cenário 8: Histórico - treinos realizados e resultados
        return sessaoTreinoRepository.findByRotinaTreinoIdAndStatusOrderByDataRealDesc(rotinaId, StatusSessaoTreino.CONCLUIDA)
                .stream()
                .map(this::toSessaoResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SessaoTreinoResponseDTO> consultarHistoricoAluno(Usuario usuario, UUID alunoId, Pageable pageable) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado para este Personal Trainer."));

        return sessaoTreinoRepository.findByAlunoIdAndStatusOrderByDataRealDesc(alunoId, StatusSessaoTreino.CONCLUIDA, pageable)
                .map(this::toSessaoResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<SessaoTreinoResponseDTO> consultarHistoricoAluno(Usuario usuario, UUID alunoId) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado para este Personal Trainer."));

        return sessaoTreinoRepository.findByAlunoIdAndStatusOrderByDataRealDesc(alunoId, StatusSessaoTreino.CONCLUIDA)
                .stream()
                .map(this::toSessaoResponseDTO)
                .toList();
    }

    public SessaoTreinoResponseDTO toSessaoResponseDTO(SessaoTreino sessao) {
        DesempenhoResponseDTO desempenhoDTO = null;
        if (sessao.getDesempenho() != null) {
            Desempenho d = sessao.getDesempenho();
            List<RegistroMetricaResponseDTO> metricasDTO = d.getRegistrosMetricas() != null ? d.getRegistrosMetricas().stream()
                    .map(rm -> new RegistroMetricaResponseDTO(
                            rm.getId(),
                            rm.getMetrica().getId(),
                            rm.getMetrica().getTitulo(),
                            rm.getMetrica().getTipo(),
                            rm.getValor()
                    ))
                    .toList() : List.of();

            desempenhoDTO = new DesempenhoResponseDTO(
                    d.getId(),
                    d.getDataRegistro(),
                    d.getNivelCansaco(),
                    d.getRegistradoPor(),
                    d.getAluno() != null ? d.getAluno().getId() : null,
                    d.getAluno() != null && d.getAluno().getUsuario() != null ? d.getAluno().getUsuario().getNome() : null,
                    metricasDTO
            );
        }

        return new SessaoTreinoResponseDTO(
                sessao.getId(),
                sessao.getRotinaTreino().getId(),
                sessao.getSemana(),
                sessao.getDataPlanejada(),
                sessao.getDataReal(),
                sessao.getDuracao(),
                sessao.getAtividades(),
                sessao.getStatus(),
                sessao.getConcluidaAluno(),
                sessao.getValidadaPersonal(),
                desempenhoDTO
        );
    }

    private boolean execucaoConfirmada(SessaoTreino sessao) {
        return Boolean.TRUE.equals(sessao.getConcluidaAluno()) || sessao.getStatus() == StatusSessaoTreino.CONCLUIDA;
    }


    private PersonalTrainer obterPersonalLogado(Usuario usuario) {
        return personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));
    }

    private RotinaTreinoResponseDTO toResponseDTO(RotinaTreino rotina) {
        List<RotinaTreinoResponseDTO.AlunoResumoDTO> alunosDTO = rotina.getAlunos().stream()
                .map(aluno -> new RotinaTreinoResponseDTO.AlunoResumoDTO(
                        aluno.getId(),
                        aluno.getUsuario() != null ? aluno.getUsuario().getNome() : null
                ))
                .toList();

        List<RotinaTreinoResponseDTO.MetricaResumoDTO> metricasDTO = rotina.getMetricas().stream()
                .map(metrica -> new RotinaTreinoResponseDTO.MetricaResumoDTO(
                        metrica.getId(),
                        metrica.getTitulo(),
                        metrica.getTipo(),
                        metrica.getObrigatoria(),
                        metrica.getQuemPreenche()
                ))
                .toList();

        return new RotinaTreinoResponseDTO(
                rotina.getId(),
                rotina.getTitulo(),
                rotina.getModalidade().getId(),
                rotina.getModalidade().getNome(),
                rotina.getFrequenciaSemanal(),
                rotina.getObjetivos(),
                rotina.getAtivo(),
                alunosDTO,
                metricasDTO
        );
    }
}