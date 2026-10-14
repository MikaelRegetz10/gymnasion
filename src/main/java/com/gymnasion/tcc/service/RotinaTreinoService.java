package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    public List<RotinaTreinoResponseDTO> listarPorPersonal(Usuario usuario) {
        PersonalTrainer personal = obterPersonalLogado(usuario);
        return rotinaTreinoRepository.findByPersonalTrainerId(personal.getId())
                .stream()
                .map(this::toResponseDTO)
                .toList();
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

        rotinaTreinoRepository.delete(rotina);
        return new MensagemResponseDTO("Rotina excluída com sucesso!");
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
    // US-10: Gerenciar rotinas de treinamento
    // =========================================================================

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

    @Transactional
    public SessaoTreinoResponseDTO registrarExecucao(Usuario usuario, UUID sessaoId, RegistrarExecucaoSessaoRequestDTO dto) {
        SessaoTreino sessao;
        Aluno alunoResponsavel;
        String registradoPor;

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
            registradoPor = "PERSONAL_TRAINER";
        } else {
            alunoResponsavel = alunoRepository.findByUsuario(usuario)
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));
            sessao = sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, alunoResponsavel.getId())
                    .orElseThrow(() -> new NotFoundException("Sessão de treino não encontrada para este aluno."));
            sessao.setConcluidaAluno(true);
            registradoPor = "ALUNO";
        }

        // US-10 Cenário 4: Data diferente da planejada - registra data efetiva e preserva a planejada
        sessao.setDataReal(dto.dataReal());
        if (dto.duracao() != null) {
            sessao.setDuracao(dto.duracao());
        }

        // US-10 Cenário 5: Confirmar conclusão - sessão concluída, registro vai ao histórico do aluno
        sessao.setStatus(StatusSessaoTreino.CONCLUIDA);

        // US-10 Cenário 3: Registrar resultados - armazena vínculo com aluno, rotina, sessão e métricas
        Desempenho desempenho = sessao.getDesempenho();
        if (desempenho == null) {
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

        boolean isPersonal = usuario.getRole() == Role.PERSONAL_TRAINER;

        Set<Metrica> metricasDaRotina = sessao.getRotinaTreino().getMetricas();
        for (RegistrarResultadoMetricaDTO resDto : dto.resultados()) {
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

            // Atualiza métrica existente ou adiciona nova (preserva métricas preenchidas por outros papéis)
            desempenho.getRegistrosMetricas().removeIf(rmExistente -> rmExistente.getMetrica().getId().equals(metrica.getId()));

            RegistroMetrica rm = RegistroMetrica.builder()
                    .desempenho(desempenho)
                    .metrica(metrica)
                    .valor(resDto.valor().trim())
                    .build();

            desempenho.getRegistrosMetricas().add(rm);
        }

        sessao = sessaoTreinoRepository.save(sessao);

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