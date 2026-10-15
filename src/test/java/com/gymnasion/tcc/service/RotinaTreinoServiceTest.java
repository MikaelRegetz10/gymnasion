package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.CampoAuditado;
import com.gymnasion.tcc.domain.enums.TipoOperacaoAuditoria;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.MetricasInvalidasException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RotinaTreinoServiceTest {

    @Mock
    private RotinaTreinoRepository rotinaTreinoRepository;

    @Mock
    private ModalidadeRepository modalidadeRepository;

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private MetricaRepository metricaRepository;

    @Mock
    private SessaoTreinoRepository sessaoTreinoRepository;

    @Mock
    private DesempenhoRepository desempenhoRepository;

    @Mock
    private AcompanhamentoTreinoService acompanhamentoTreinoService;

    @Mock
    private AuditoriaDesempenhoRepository auditoriaDesempenhoRepository;

    @InjectMocks

    private RotinaTreinoService rotinaTreinoService;

    private Usuario usuarioPersonal;
    private PersonalTrainer personalTrainer;
    private Usuario usuarioAluno;
    private Aluno aluno;
    private Modalidade modalidade;
    private Metrica metrica1;
    private Metrica metrica2;
    private RotinaTreino rotinaTreino;

    @BeforeEach
    void setUp() {
        usuarioPersonal = Usuario.builder()
                .id(UUID.randomUUID())
                .email("personal@gymnasion.com")
                .nome("Treinador Carlos")
                .role(Role.PERSONAL_TRAINER)
                .build();

        personalTrainer = PersonalTrainer.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioPersonal)
                .build();

        usuarioAluno = Usuario.builder()
                .id(UUID.randomUUID())
                .email("aluno@gymnasion.com")
                .nome("Aluno Lucas")
                .role(Role.ALUNO)
                .build();

        aluno = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .personal(personalTrainer)
                .build();

        modalidade = Modalidade.builder()
                .id(1L)
                .nome("Natação")
                .build();

        metrica1 = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Tempo 50m")
                .tipo(TipoMetrica.TEMPO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .build();

        metrica2 = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Braçadas")
                .tipo(TipoMetrica.NUMERICO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .build();

        rotinaTreino = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Rotina Pré-Competição")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .frequenciaSemanal(3)
                .objetivos("Melhorar tempo nos 50m")
                .alunos(new HashSet<>(Set.of(aluno)))
                .metricas(new HashSet<>(Set.of(metrica1, metrica2)))
                .build();
    }

    @Test
    @DisplayName("US10 Cenário 1: Consultar planejamento por semanas com sessões e atividades")
    void US10_cenario1_consultarPlanejamentoPorSemanasComSessoesEAtividades() {
        // Dado: uma rotina existente com 2 sessões em semanas diferentes
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));

        SessaoTreino s1 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .duracao(60)
                .atividades("Aquecimento e 4x50m")
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        SessaoTreino s2 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(2)
                .dataPlanejada(LocalDate.of(2026, 10, 27))
                .duracao(45)
                .atividades("Tiro curto e recuperação")
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaTreino.getId()))
                .thenReturn(List.of(s1, s2));

        // Quando: consulta o planejamento
        PlanejamentoRotinaResponseDTO response = rotinaTreinoService.consultarPlanejamento(usuarioPersonal, rotinaTreino.getId());

        // Então: retorna planejamento agrupado por semanas
        assertThat(response).isNotNull();
        assertThat(response.rotinaId()).isEqualTo(rotinaTreino.getId());
        assertThat(response.semanas()).hasSize(2);
        assertThat(response.semanas().get(0).numeroSemana()).isEqualTo(1);
        assertThat(response.semanas().get(0).sessoes().get(0).atividades()).isEqualTo("Aquecimento e 4x50m");
        assertThat(response.semanas().get(1).numeroSemana()).isEqualTo(2);
    }

    @Test
    @DisplayName("US10 Cenário 2: Editar sessões futuras não executadas com sucesso")
    void US10_cenario2_editarSessaoFuturaNaoExecutadaComSucesso() {
        // Dado: uma sessão planejada (não executada)
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .duracao(60)
                .atividades("Atividade antiga")
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessao));
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AtualizarSessaoTreinoRequestDTO dto = new AtualizarSessaoTreinoRequestDTO(
                2,
                LocalDate.of(2026, 10, 22),
                75,
                "Nova atividade com foco técnico"
        );

        // Quando: edita a sessão futura
        SessaoTreinoResponseDTO response = rotinaTreinoService.editarSessaoFutura(usuarioPersonal, sessaoId, dto);

        // Então: campos são atualizados
        assertThat(response.semana()).isEqualTo(2);
        assertThat(response.dataPlanejada()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(response.duracao()).isEqualTo(75);
        assertThat(response.atividades()).isEqualTo("Nova atividade com foco técnico");
    }

    @Test
    @DisplayName("US10 Cenário 2: Bloquear edição de sessão futura caso a sessão já tenha sido executada")
    void US10_cenario2_bloquearEdicaoDeSessaoJaExecutada() {
        // Dado: uma sessão já concluída / executada
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessaoExecutada = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 10))
                .dataReal(LocalDate.of(2026, 10, 10))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessaoExecutada));

        AtualizarSessaoTreinoRequestDTO dto = new AtualizarSessaoTreinoRequestDTO(
                1,
                LocalDate.of(2026, 10, 15),
                60,
                "Tentativa de alteração de histórico"
        );

        // Quando & Então: tenta editar sessão executada e é bloqueado com BusinessException
        assertThatThrownBy(() -> rotinaTreinoService.editarSessaoFutura(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não é possível editar uma sessão de treino já executada. Os dados históricos devem ser preservados.");

        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US10 Cenário 3 e 4: Registrar execução com data diferente preserva data planejada")
    void US10_cenario3_e_4_dataRealDiferentePreservaDataPlanejada() {
        // Dado: sessão com data planejada 20/10/2026 executada pelo aluno em 22/10/2026
        UUID sessaoId = UUID.randomUUID();
        LocalDate dataPlanejadaOriginal = LocalDate.of(2026, 10, 20);
        LocalDate dataEfetivaReal = LocalDate.of(2026, 10, 22);

        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(dataPlanejadaOriginal)
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())).thenReturn(Optional.of(sessao));
        when(metricaRepository.findById(metrica1.getId())).thenReturn(Optional.of(metrica1));
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                dataEfetivaReal,
                50,
                8, // RPE Borg CR-10
                null,
                List.of(new RegistrarResultadoMetricaDTO(metrica1.getId(), "00:29"))
        );

        // Quando: registra a execução
        SessaoTreinoResponseDTO response = rotinaTreinoService.registrarExecucao(usuarioAluno, sessaoId, dto);

        // Então: data planejada é PRESERVADA e data real é registrada
        assertThat(response.dataPlanejada()).isEqualTo(dataPlanejadaOriginal);
        assertThat(response.dataReal()).isEqualTo(dataEfetivaReal);
        assertThat(response.status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
        assertThat(response.concluidaAluno()).isTrue();

        // E: resultados e RPE são armazenados
        assertThat(response.desempenho()).isNotNull();
        assertThat(response.desempenho().nivelCansaco()).isEqualTo(8);
        assertThat(response.desempenho().metricas()).hasSize(1);
        assertThat(response.desempenho().metricas().get(0).tituloMetrica()).isEqualTo("Tempo 50m");
        assertThat(response.desempenho().metricas().get(0).valor()).isEqualTo("00:29");
    }

    @Test
    @DisplayName("US10 Cenário 6: Validar execução pelo personal mantém conclusão do aluno")
    void US10_cenario6_validarExecucaoPersonalMantemConclusaoDoAluno() {
        // Dado: uma sessão concluída pelo aluno
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessaoConcluida = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .dataReal(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .validadaPersonal(false)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessaoConcluida));
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Quando: personal valida a execução
        SessaoTreinoResponseDTO response = rotinaTreinoService.validarExecucao(usuarioPersonal, sessaoId);

        // Então: validadaPersonal passa para true e concluidaAluno permanece true
        assertThat(response.validadaPersonal()).isTrue();
        assertThat(response.concluidaAluno()).isTrue();
    }

    @Test
    @DisplayName("US10 Cenário 7: Personal trainer registra execução vinculando aluno e métricas")
    void US10_cenario7_personalRegistraExecucao() {
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(metricaRepository.findById(metrica2.getId())).thenReturn(Optional.of(metrica2));
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                null,
                aluno.getId(),
                List.of(new RegistrarResultadoMetricaDTO(metrica2.getId(), "34"))
        );

        // Quando: personal registra
        SessaoTreinoResponseDTO response = rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto);

        // Então: registrado com sucesso pelo personal
        assertThat(response.status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
        assertThat(response.desempenho().registradoPor()).isEqualTo("PERSONAL_TRAINER");
        assertThat(response.desempenho().metricas().get(0).tituloMetrica()).isEqualTo("Braçadas");
        assertThat(response.desempenho().metricas().get(0).valor()).isEqualTo("34");
    }

    @Test
    @DisplayName("US10 Cenário 8: Consultar histórico de treinos concluídos")
    void US10_cenario8_consultarHistoricoDeTreinosRealizados() {
        // Dado: uma rotina com treinos concluídos
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));

        SessaoTreino s1 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 15))
                .dataReal(LocalDate.of(2026, 10, 15))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .build();

        when(sessaoTreinoRepository.findByRotinaTreinoIdAndStatusOrderByDataRealDesc(rotinaTreino.getId(), StatusSessaoTreino.CONCLUIDA))
                .thenReturn(List.of(s1));

        // Quando: consulta histórico da rotina
        List<SessaoTreinoResponseDTO> historico = rotinaTreinoService.consultarHistoricoRotina(usuarioPersonal, rotinaTreino.getId());

        // Então: retorna os treinos realizados
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
        assertThat(historico.get(0).dataReal()).isEqualTo(LocalDate.of(2026, 10, 15));
    }
    @Test
    @DisplayName("US18 Cenário 5 / US10 Cenário 4: Personal não pode preencher métrica exclusiva do aluno")
    void US18_cenario5_personalNaoPodePreencherMetricaExclusivaDoAluno() {
        // Dado: métrica configurada como exclusiva do aluno
        Metrica metricaExclusivaAluno = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Sensação Subjetiva")
                .tipo(TipoMetrica.TEXTO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .quemPreenche(ResponsavelPreenchimento.ALUNO)
                .build();

        RotinaTreino rotinaComMetricaAluno = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Rotina Teste")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .frequenciaSemanal(3)
                .alunos(new HashSet<>(Set.of(aluno)))
                .metricas(new HashSet<>(Set.of(metricaExclusivaAluno)))
                .build();

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaComMetricaAluno)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(metricaRepository.findById(metricaExclusivaAluno.getId())).thenReturn(Optional.of(metricaExclusivaAluno));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                null,
                aluno.getId(),
                List.of(new RegistrarResultadoMetricaDTO(metricaExclusivaAluno.getId(), "Ótimo"))
        );

        // Quando & Então: personal tenta preencher métrica exclusiva do aluno e é bloqueado
        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("é de preenchimento exclusivo do aluno");

        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US10 Cenário 4 / US18 Cenário 5: Aluno não pode preencher métrica exclusiva do personal")
    void US10_cenario4_alunoNaoPodePreencherMetricaExclusivaDoPersonal() {
        // Dado: métrica configurada como exclusiva do personal
        Metrica metricaExclusivaPersonal = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Análise Técnica do Nado")
                .tipo(TipoMetrica.TEXTO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .quemPreenche(ResponsavelPreenchimento.PERSONAL)
                .build();

        RotinaTreino rotinaComMetricaPersonal = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Rotina Avançada")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .frequenciaSemanal(4)
                .alunos(new HashSet<>(Set.of(aluno)))
                .metricas(new HashSet<>(Set.of(metricaExclusivaPersonal)))
                .build();

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaComMetricaPersonal)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())).thenReturn(Optional.of(sessao));
        when(metricaRepository.findById(metricaExclusivaPersonal.getId())).thenReturn(Optional.of(metricaExclusivaPersonal));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                7,
                null,
                List.of(new RegistrarResultadoMetricaDTO(metricaExclusivaPersonal.getId(), "Braço direito com erro"))
        );

        // Quando & Então: aluno tenta preencher métrica exclusiva do personal e é bloqueado
        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioAluno, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("é de preenchimento exclusivo do personal trainer");

        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    // =========================================================================
    // US-11: Vinculação de treino ao aluno e Trava de 1 rotina ativa
    // =========================================================================

    @Test
    @DisplayName("US11 Cenário 1: Selecionar 1+ alunos válidos vincula com sucesso à rotina")
    void US11_cenario1_vincularAlunosValidosComSucesso() {
        // Dado: uma rotina existente sem alunos vinculados
        RotinaTreino rotinaSemAlunos = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Rotina Base")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .frequenciaSemanal(3)
                .ativo(true)
                .alunos(new HashSet<>())
                .metricas(new HashSet<>(Set.of(metrica1)))
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaSemAlunos.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaSemAlunos));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.existsByAlunoIdAndAtivoTrueAndIdNot(aluno.getId(), rotinaSemAlunos.getId()))
                .thenReturn(false);
        when(rotinaTreinoRepository.save(any(RotinaTreino.class))).thenAnswer(i -> i.getArgument(0));

        VincularAlunosRotinaRequestDTO dto = new VincularAlunosRotinaRequestDTO(Set.of(aluno.getId()));

        // Quando: vincula o aluno
        RotinaTreinoResponseDTO response = rotinaTreinoService.vincularAlunos(usuarioPersonal, rotinaSemAlunos.getId(), dto);

        // Então: rotina agora contém o aluno
        assertThat(response).isNotNull();
        assertThat(response.alunos()).hasSize(1);
        assertThat(response.alunos().get(0).id()).isEqualTo(aluno.getId());
    }

    @Test
    @DisplayName("US11 Cenário 2: Aluno já vinculado a este treino é bloqueado com mensagem da DRS")
    void US11_cenario2_alunoJaVinculadoBloqueado() {
        // Dado: rotina que já possui o aluno vinculado
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));

        VincularAlunosRotinaRequestDTO dto = new VincularAlunosRotinaRequestDTO(Set.of(aluno.getId()));

        // Quando & Então: tenta vincular novamente e recebe erro exato do DRS
        assertThatThrownBy(() -> rotinaTreinoService.vincularAlunos(usuarioPersonal, rotinaTreino.getId(), dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esse aluno já possui o treino vinculado.");

        verify(rotinaTreinoRepository, never()).save(any(RotinaTreino.class));
    }

    @Test
    @DisplayName("US11 Cenário 3: Nenhum aluno selecionado bloqueia com mensagem da DRS")
    void US11_cenario3_nenhumAlunoSelecionadoBloqueado() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));

        VincularAlunosRotinaRequestDTO dtoVazio = new VincularAlunosRotinaRequestDTO(Set.of());

        // Quando & Então: tenta vincular com conjunto vazio
        assertThatThrownBy(() -> rotinaTreinoService.vincularAlunos(usuarioPersonal, rotinaTreino.getId(), dtoVazio))
                .isInstanceOf(BusinessException.class)
                .hasMessage("É necessário selecionar pelo menos 1 (um) aluno.");

        // E com DTO nulo
        assertThatThrownBy(() -> rotinaTreinoService.vincularAlunos(usuarioPersonal, rotinaTreino.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("É necessário selecionar pelo menos 1 (um) aluno.");
    }

    @Test
    @DisplayName("US11 Trava 1 Rotina Ativa: Bloquear vinculação se aluno já possui outra rotina ativa")
    void US11_trava1RotinaAtiva_bloquearVinculacaoSeAlunoJaTemOutraRotinaAtiva() {
        // Dado: rotina B vazia e aluno já possui uma rotina A ativa
        RotinaTreino rotinaB = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Rotina B")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .frequenciaSemanal(2)
                .ativo(true)
                .alunos(new HashSet<>())
                .metricas(new HashSet<>(Set.of(metrica1)))
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaB.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaB));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.existsByAlunoIdAndAtivoTrueAndIdNot(aluno.getId(), rotinaB.getId()))
                .thenReturn(true);

        VincularAlunosRotinaRequestDTO dto = new VincularAlunosRotinaRequestDTO(Set.of(aluno.getId()));

        // Quando & Então: bloqueado por ter outra rotina ativa
        assertThatThrownBy(() -> rotinaTreinoService.vincularAlunos(usuarioPersonal, rotinaB.getId(), dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já possui uma rotina de treino ativa. A rotina atual precisa ser encerrada antes.");

        verify(rotinaTreinoRepository, never()).save(any(RotinaTreino.class));
    }

    @Test
    @DisplayName("US11 Trava 1 Rotina Ativa: Bloquear criação de nova rotina se aluno já tem rotina ativa")
    void US11_trava1RotinaAtiva_bloquearCriacaoRotinaSeAlunoJaTemRotinaAtiva() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(modalidadeRepository.findById(modalidade.getId())).thenReturn(Optional.of(modalidade));
        when(metricaRepository.findByPersonalTrainerIdAndModalidadeId(personalTrainer.getId(), modalidade.getId()))
                .thenReturn(List.of(metrica1));
        when(metricaRepository.findByIdAndPersonalTrainerId(metrica1.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(metrica1));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.existsByAlunoIdAndAtivoTrue(aluno.getId())).thenReturn(true);

        CriarRotinaTreinoRequestDTO dto = new CriarRotinaTreinoRequestDTO(
                "Nova Rotina C",
                modalidade.getId(),
                3,
                "Objetivo",
                Set.of(aluno.getId()),
                Set.of(metrica1.getId())
        );

        // Quando & Então: bloqueado na criação da rotina
        assertThatThrownBy(() -> rotinaTreinoService.criarRotinaTreino(usuarioPersonal, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já possui uma rotina de treino ativa. A rotina atual precisa ser encerrada antes.");

        verify(rotinaTreinoRepository, never()).save(any(RotinaTreino.class));
    }

    @Test
    @DisplayName("US11 Desvincular Aluno: Remove aluno com sucesso mantendo outros alunos")
    void US11_desvincularAlunoComSucesso() {
        // Dado: rotina com 2 alunos
        Aluno aluno2 = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(Usuario.builder().nome("Aluno 2").build())
                .personal(personalTrainer)
                .build();

        RotinaTreino rotinaComDoisAlunos = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Rotina Dupla")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .frequenciaSemanal(3)
                .alunos(new HashSet<>(Set.of(aluno, aluno2)))
                .metricas(new HashSet<>(Set.of(metrica1)))
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaComDoisAlunos.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaComDoisAlunos));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.save(any(RotinaTreino.class))).thenAnswer(i -> i.getArgument(0));

        // Quando: desvincula o primeiro aluno
        RotinaTreinoResponseDTO response = rotinaTreinoService.desvincularAluno(usuarioPersonal, rotinaComDoisAlunos.getId(), aluno.getId());

        // Então: apenas o segundo aluno permanece
        assertThat(response.alunos()).hasSize(1);
        assertThat(response.alunos().get(0).id()).isEqualTo(aluno2.getId());
    }

    @Test
    @DisplayName("US11 / RN-012: Bloquear desvinculação do único aluno da rotina")
    void US11_desvincularUnicoAlunoBloqueadoPelaRN012() {
        // Dado: rotina com apenas 1 aluno
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));

        // Quando & Então: tenta desvincular o único aluno
        assertThatThrownBy(() -> rotinaTreinoService.desvincularAluno(usuarioPersonal, rotinaTreino.getId(), aluno.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A rotina deve possuir pelo menos um aluno vinculado. Para remover todos os alunos, exclua a rotina.");

        verify(rotinaTreinoRepository, never()).save(any(RotinaTreino.class));
    }

    @Test
    @DisplayName("US11 / RN-014: Bloquear desvinculação de aluno em rotina já encerrada para preservar histórico")
    void US11_desvincularAlunoEmRotinaEncerradaBloqueado() {
        RotinaTreino rotinaEncerrada = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Rotina Encerrada")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .frequenciaSemanal(3)
                .ativo(false)
                .alunos(new HashSet<>(Set.of(aluno)))
                .metricas(new HashSet<>(Set.of(metrica1)))
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaEncerrada.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaEncerrada));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> rotinaTreinoService.desvincularAluno(usuarioPersonal, rotinaEncerrada.getId(), aluno.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não é possível desvincular alunos de uma rotina de treino já encerrada. O histórico de treinos do aluno deve ser preservado.");

        verify(rotinaTreinoRepository, never()).save(any(RotinaTreino.class));
    }


    @Test
    @DisplayName("US25 Cenário 7: Encerrar rotina de treino remove sessões planejadas futuras e desativa")
    void US25_cenario7_encerrarRotinaComSucesso() {
        SessaoTreino sessaoPlanejada = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(2)
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        SessaoTreino sessaoConcluida = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .status(StatusSessaoTreino.CONCLUIDA)
                .build();

        rotinaTreino.setSessoes(new ArrayList<>(List.of(sessaoPlanejada, sessaoConcluida)));
        rotinaTreino.setAtivo(true);

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));
        when(rotinaTreinoRepository.save(any(RotinaTreino.class))).thenAnswer(i -> i.getArgument(0));

        // Quando: encerra a rotina
        RotinaTreinoResponseDTO response = rotinaTreinoService.encerrarRotina(usuarioPersonal, rotinaTreino.getId());

        // Então: rotina agora está inativa/encerrada e sessões planejadas foram removidas
        assertThat(response.ativo()).isFalse();
        assertThat(rotinaTreino.getSessoes()).hasSize(1);
        assertThat(rotinaTreino.getSessoes().get(0).getStatus()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
    }

    @Test
    @DisplayName("US22 Cenário 8: Aluno não pode registrar novamente uma sessão já confirmada")
    void US22_cenario8_bloquearReexecucaoPeloAluno() {
        // Dado: sessão cuja execução já foi confirmada pelo aluno
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessaoConfirmada = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .dataReal(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())).thenReturn(Optional.of(sessaoConfirmada));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 21),
                45,
                6,
                null,
                List.of(new RegistrarResultadoMetricaDTO(metrica1.getId(), "00:31"))
        );

        // Quando & Então: nova tentativa de registro é bloqueada e os dados originais são preservados
        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioAluno, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já foi confirmada e não pode ser alterada");

        assertThat(sessaoConfirmada.getDataReal()).isEqualTo(LocalDate.of(2026, 10, 20));
        verify(metricaRepository, never()).findById(any());
        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US22 Cenário 8: Personal continua podendo registrar a execução de sessão concluída")
    void US22_cenario8_travaNaoSeAplicaAoPersonal() {
        // Dado: sessão já concluída pelo aluno
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessaoConfirmada = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessaoConfirmada));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(metricaRepository.findById(metrica2.getId())).thenReturn(Optional.of(metrica2));
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                null,
                null,
                aluno.getId(),
                List.of(new RegistrarResultadoMetricaDTO(metrica2.getId(), "42"))
        );

        // Quando
        SessaoTreinoResponseDTO response = rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto);

        // Então: o registro do personal é aceito
        assertThat(response.status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
        assertThat(response.desempenho().metricas()).hasSize(1);
        assertThat(response.desempenho().metricas().get(0).valor()).isEqualTo("42");
    }

    @Test
    @DisplayName("US22 Cenário 9: Aceita valores válidos para todos os tipos de métrica")
    void US22_cenario9_aceitaValoresValidosPorTipo() {
        // Dado: rotina com uma métrica de cada tipo
        Metrica fc = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("FC Média")
                .tipo(TipoMetrica.FREQUENCIA_CARDIACA)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .build();
        Metrica observacao = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Observação")
                .tipo(TipoMetrica.TEXTO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .build();
        rotinaTreino.getMetricas().addAll(Set.of(fc, observacao));

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())).thenReturn(Optional.of(sessao));
        when(metricaRepository.findById(metrica1.getId())).thenReturn(Optional.of(metrica1));
        when(metricaRepository.findById(metrica2.getId())).thenReturn(Optional.of(metrica2));
        when(metricaRepository.findById(fc.getId())).thenReturn(Optional.of(fc));
        when(metricaRepository.findById(observacao.getId())).thenReturn(Optional.of(observacao));
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                7,
                null,
                List.of(
                        new RegistrarResultadoMetricaDTO(metrica1.getId(), " 01:15 "),
                        new RegistrarResultadoMetricaDTO(metrica2.getId(), "32,5"),
                        new RegistrarResultadoMetricaDTO(fc.getId(), "145"),
                        new RegistrarResultadoMetricaDTO(observacao.getId(), "Senti cansaço no final")
                )
        );

        // Quando
        SessaoTreinoResponseDTO response = rotinaTreinoService.registrarExecucao(usuarioAluno, sessaoId, dto);

        // Então: todos os valores são aceitos e armazenados
        assertThat(response.status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
        assertThat(response.desempenho().metricas())
                .extracting(RegistroMetricaResponseDTO::valor)
                .containsExactlyInAnyOrder("01:15", "32,5", "145", "Senti cansaço no final");
    }

    @Test
    @DisplayName("US22 Cenários 9 e 10: Valores inválidos impedem a confirmação e identificam os campos")
    void US22_cenario9_e_10_valoresInvalidosIdentificamCampos() {
        // Dado: rotina com métricas de tempo, numérica e frequência cardíaca
        Metrica fc = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("FC Máxima")
                .tipo(TipoMetrica.FREQUENCIA_CARDIACA)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .build();
        rotinaTreino.getMetricas().add(fc);

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())).thenReturn(Optional.of(sessao));
        when(metricaRepository.findById(metrica1.getId())).thenReturn(Optional.of(metrica1));
        when(metricaRepository.findById(metrica2.getId())).thenReturn(Optional.of(metrica2));
        when(metricaRepository.findById(fc.getId())).thenReturn(Optional.of(fc));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                7,
                null,
                List.of(
                        new RegistrarResultadoMetricaDTO(metrica1.getId(), "1:75"),
                        new RegistrarResultadoMetricaDTO(metrica2.getId(), "trinta"),
                        new RegistrarResultadoMetricaDTO(fc.getId(), "400")
                )
        );

        // Quando & Então: a confirmação é impedida e todos os campos inválidos são identificados
        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioAluno, sessaoId, dto))
                .isInstanceOf(MetricasInvalidasException.class)
                .satisfies(ex -> {
                    Map<String, String> campos = ((MetricasInvalidasException) ex).getCamposInvalidos();
                    assertThat(campos).containsOnlyKeys(
                            metrica1.getId().toString(),
                            metrica2.getId().toString(),
                            fc.getId().toString()
                    );
                    assertThat(campos.get(metrica1.getId().toString())).contains("formato mm:ss");
                    assertThat(campos.get(metrica2.getId().toString())).contains("deve ser numérico");
                    assertThat(campos.get(fc.getId().toString())).contains("bpm");
                });

        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US22 Cenário 1: Aluno acessa o registro com as métricas configuradas pelo personal")
    void US22_cenario1_acessarFormularioComMetricasConfiguradas() {
        // Dado: rotina com métricas ativas (inclusive exclusiva do personal) e uma métrica descontinuada
        Metrica exclusivaPersonal = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Análise Técnica")
                .tipo(TipoMetrica.TEXTO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .obrigatoria(true)
                .quemPreenche(ResponsavelPreenchimento.PERSONAL)
                .build();
        Metrica descontinuada = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Métrica Antiga")
                .tipo(TipoMetrica.NUMERICO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(false)
                .build();
        rotinaTreino.getMetricas().addAll(Set.of(exclusivaPersonal, descontinuada));

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())).thenReturn(Optional.of(sessao));

        // Quando
        FormularioExecucaoResponseDTO formulario = rotinaTreinoService.obterFormularioExecucao(usuarioAluno, sessaoId);

        // Então: a sessão está disponível para registro e as métricas ativas são apresentadas com sua configuração
        assertThat(formulario.registroPermitido()).isTrue();
        assertThat(formulario.sessao().id()).isEqualTo(sessaoId);
        assertThat(formulario.metricas())
                .extracting(RotinaTreinoResponseDTO.MetricaResumoDTO::titulo)
                .containsExactly("Análise Técnica", "Braçadas", "Tempo 50m");
        assertThat(formulario.metricas().get(0).quemPreenche()).isEqualTo(ResponsavelPreenchimento.PERSONAL);
        assertThat(formulario.metricas().get(0).obrigatoria()).isTrue();
    }

    @Test
    @DisplayName("US22 Cenário 1 e 8: Sessão já confirmada é exibida sem permitir novo registro")
    void US22_cenario1_e_8_formularioDeSessaoConfirmadaNaoPermiteRegistro() {
        // Dado: sessão já confirmada pelo aluno
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessaoConfirmada = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .dataReal(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())).thenReturn(Optional.of(sessaoConfirmada));

        // Quando
        FormularioExecucaoResponseDTO formulario = rotinaTreinoService.obterFormularioExecucao(usuarioAluno, sessaoId);

        // Então: os dados são exibidos apenas para consulta
        assertThat(formulario.registroPermitido()).isFalse();
        assertThat(formulario.sessao().status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
    }

    @Test
    @DisplayName("US22 Cenário 3: Impede a confirmação sem as métricas obrigatórias do aluno e informa quais faltam")
    void US22_cenario3_bloquearConfirmacaoSemMetricasObrigatorias() {
        // Dado: rotina com métricas obrigatórias do aluno (ALUNO e AMBOS)
        metrica1.setObrigatoria(true);
        Metrica obrigatoriaAluno = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Percepção do Treino")
                .tipo(TipoMetrica.TEXTO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .obrigatoria(true)
                .quemPreenche(ResponsavelPreenchimento.ALUNO)
                .build();
        rotinaTreino.getMetricas().add(obrigatoriaAluno);

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())).thenReturn(Optional.of(sessao));
        when(metricaRepository.findById(metrica2.getId())).thenReturn(Optional.of(metrica2));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                7,
                null,
                List.of(new RegistrarResultadoMetricaDTO(metrica2.getId(), "30"))
        );

        // Quando & Então: a confirmação é impedida e os campos obrigatórios faltantes são informados
        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioAluno, sessaoId, dto))
                .isInstanceOf(MetricasInvalidasException.class)
                .satisfies(ex -> {
                    Map<String, String> campos = ((MetricasInvalidasException) ex).getCamposInvalidos();
                    assertThat(campos).containsOnlyKeys(metrica1.getId().toString(), obrigatoriaAluno.getId().toString());
                    assertThat(campos.get(metrica1.getId().toString())).contains("'Tempo 50m' é obrigatória");
                    assertThat(campos.get(obrigatoriaAluno.getId().toString())).contains("'Percepção do Treino' é obrigatória");
                });

        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US22 Cenários 4 e 5: Confirma sem métricas opcionais e mantém as exclusivas do personal pendentes")
    void US22_cenario4_e_5_confirmarSemOpcionaisESemExclusivasDoPersonal() {
        // Dado: rotina com métricas opcionais e uma obrigatória exclusiva do personal; uma obrigatória descontinuada
        Metrica obrigatoriaPersonal = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Análise Técnica")
                .tipo(TipoMetrica.TEXTO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .obrigatoria(true)
                .quemPreenche(ResponsavelPreenchimento.PERSONAL)
                .build();
        Metrica obrigatoriaDescontinuada = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Métrica Antiga")
                .tipo(TipoMetrica.NUMERICO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .obrigatoria(true)
                .ativo(false)
                .build();
        rotinaTreino.getMetricas().addAll(Set.of(obrigatoriaPersonal, obrigatoriaDescontinuada));

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(sessaoTreinoRepository.findByIdAndAlunoId(sessaoId, aluno.getId())).thenReturn(Optional.of(sessao));
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Quando: aluno confirma sem preencher nenhuma métrica (lista ausente)
        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                6,
                null,
                null
        );
        SessaoTreinoResponseDTO response = rotinaTreinoService.registrarExecucao(usuarioAluno, sessaoId, dto);

        // Então: a sessão é concluída sem atribuir valores inexistentes
        assertThat(response.status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
        assertThat(response.concluidaAluno()).isTrue();
        assertThat(response.desempenho()).isNotNull();
        assertThat(response.desempenho().metricas()).isEmpty();
        verify(metricaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("US10: Personal continua obrigado a informar ao menos um resultado de métrica")
    void US10_personalDeveInformarAoMenosUmResultado() {
        // Dado: sessão da rotina do personal
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                null,
                aluno.getId(),
                List.of()
        );

        // Quando & Então
        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("É necessário informar pelo menos um resultado de métrica.");

        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US25 Cenários 1 e 4: Reutilizar rotina pela lista de rotinas e recalcular datas de semanas e sessões")
    void US25_cenarios1_4_reutilizarRotinaComRecalculoDeDatas() {
        // Dado: rotina original com 2 semanas e 3 sessões planejadas
        SessaoTreino s1 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 3, 2)) // Segunda
                .duracao(60)
                .atividades("Treino de Nado Crawl")
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        SessaoTreino s2 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 3, 4)) // Quarta (+2 dias)
                .duracao(45)
                .atividades("Treino de Nado Costas")
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        SessaoTreino s3 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(2)
                .dataPlanejada(LocalDate.of(2026, 3, 9)) // Segunda seguinte (+7 dias)
                .duracao(50)
                .atividades("Treino de Resistência")
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        rotinaTreino.setSessoes(new ArrayList<>(List.of(s1, s2, s3)));

        LocalDate novaDataInicio = LocalDate.of(2026, 11, 2); // Nova data de início (Segunda)
        ReutilizarRotinaRequestDTO dto = new ReutilizarRotinaRequestDTO(
                aluno.getId(),
                novaDataInicio,
                "Rotina Velocidade Reutilizada",
                "Foco em velocidade pura"
        );

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.existsByAlunoIdAndAtivoTrue(aluno.getId())).thenReturn(false);

        ArgumentCaptor<RotinaTreino> rotinaCaptor = ArgumentCaptor.forClass(RotinaTreino.class);
        when(rotinaTreinoRepository.save(rotinaCaptor.capture())).thenAnswer(i -> {
            RotinaTreino r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        // Quando: reutiliza a rotina
        RotinaTreinoResponseDTO response = rotinaTreinoService.reutilizarRotina(usuarioPersonal, rotinaTreino.getId(), dto);

        // Então: nova rotina criada como ativa com título/objetivos personalizados
        assertThat(response.ativo()).isTrue();
        assertThat(response.titulo()).isEqualTo("Rotina Velocidade Reutilizada");

        RotinaTreino novaRotinaSalva = rotinaCaptor.getValue();
        assertThat(novaRotinaSalva.getAtivo()).isTrue();
        assertThat(novaRotinaSalva.getAlunos()).containsExactly(aluno);
        assertThat(novaRotinaSalva.getMetricas()).containsExactlyInAnyOrder(metrica1, metrica2);
        assertThat(novaRotinaSalva.getSessoes()).hasSize(3);

        // Verifica o recálculo das datas (mantendo intervalos originais)
        SessaoTreino ns1 = novaRotinaSalva.getSessoes().get(0);
        SessaoTreino ns2 = novaRotinaSalva.getSessoes().get(1);
        SessaoTreino ns3 = novaRotinaSalva.getSessoes().get(2);

        assertThat(ns1.getDataPlanejada()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(ns1.getSemana()).isEqualTo(1);
        assertThat(ns1.getAtividades()).isEqualTo("Treino de Nado Crawl");
        assertThat(ns1.getStatus()).isEqualTo(StatusSessaoTreino.PLANEJADA);

        assertThat(ns2.getDataPlanejada()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(ns2.getSemana()).isEqualTo(1);
        assertThat(ns2.getStatus()).isEqualTo(StatusSessaoTreino.PLANEJADA);

        assertThat(ns3.getDataPlanejada()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(ns3.getSemana()).isEqualTo(2);
        assertThat(ns3.getStatus()).isEqualTo(StatusSessaoTreino.PLANEJADA);
    }

    @Test
    @DisplayName("US25 Cenário 2: Atribuir rotina a partir do perfil do aluno")
    void US25_cenario2_atribuirRotinaPeloPerfilDoAluno() {
        LocalDate novaDataInicio = LocalDate.of(2026, 12, 1);
        AtribuirRotinaAlunoRequestDTO dto = new AtribuirRotinaAlunoRequestDTO(
                rotinaTreino.getId(),
                novaDataInicio,
                null,
                null
        );

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.existsByAlunoIdAndAtivoTrue(aluno.getId())).thenReturn(false);
        when(rotinaTreinoRepository.save(any(RotinaTreino.class))).thenAnswer(i -> {
            RotinaTreino r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RotinaTreinoResponseDTO response = rotinaTreinoService.atribuirRotina(usuarioPersonal, aluno.getId(), dto);

        assertThat(response.ativo()).isTrue();
        assertThat(response.titulo()).isEqualTo(rotinaTreino.getTitulo());
    }

    @Test
    @DisplayName("US25 Cenários 3 e 9: Copiar estrutura não copia resultados, datas reais nem histórico de execução")
    void US25_cenarios3_9_copiarEstruturaNaoCopiaResultadosNemHistorico() {
        SessaoTreino sessaoExecutadaOrigem = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 3, 2))
                .dataReal(LocalDate.of(2026, 3, 3))
                .duracao(55)
                .atividades("Atividade 1")
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .validadaPersonal(true)
                .desempenho(Desempenho.builder()
                        .id(UUID.randomUUID())
                        .dataRegistro(java.time.OffsetDateTime.now())
                        .nivelCansaco(4)
                        .registradoPor("ALUNO")
                        .build())
                .build();

        rotinaTreino.setSessoes(new ArrayList<>(List.of(sessaoExecutadaOrigem)));

        ReutilizarRotinaRequestDTO dto = new ReutilizarRotinaRequestDTO(
                aluno.getId(),
                LocalDate.of(2026, 11, 2),
                null,
                null
        );

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.existsByAlunoIdAndAtivoTrue(aluno.getId())).thenReturn(false);

        ArgumentCaptor<RotinaTreino> rotinaCaptor = ArgumentCaptor.forClass(RotinaTreino.class);
        when(rotinaTreinoRepository.save(rotinaCaptor.capture())).thenAnswer(i -> {
            RotinaTreino r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        rotinaTreinoService.reutilizarRotina(usuarioPersonal, rotinaTreino.getId(), dto);

        RotinaTreino salva = rotinaCaptor.getValue();
        assertThat(salva.getSessoes()).hasSize(1);
        SessaoTreino novaSessao = salva.getSessoes().get(0);

        assertThat(novaSessao.getStatus()).isEqualTo(StatusSessaoTreino.PLANEJADA);
        assertThat(novaSessao.getDataReal()).isNull();
        assertThat(novaSessao.getDesempenho()).isNull();
        assertThat(novaSessao.getConcluidaAluno()).isFalse();
        assertThat(novaSessao.getValidadaPersonal()).isFalse();
    }

    @Test
    @DisplayName("US25 Cenário 6: Bloquear reutilização se o aluno já possui rotina de treino ativa")
    void US25_cenario6_bloquearReutilizacaoSeAlunoJaPossuiRotinaAtiva() {
        ReutilizarRotinaRequestDTO dto = new ReutilizarRotinaRequestDTO(
                aluno.getId(),
                LocalDate.of(2026, 11, 2),
                null,
                null
        );

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.existsByAlunoIdAndAtivoTrue(aluno.getId())).thenReturn(true);

        assertThatThrownBy(() -> rotinaTreinoService.reutilizarRotina(usuarioPersonal, rotinaTreino.getId(), dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O aluno " + aluno.getUsuario().getNome() + " já possui uma rotina de treino ativa. A rotina atual precisa ser encerrada antes.");

        verify(rotinaTreinoRepository, never()).save(any(RotinaTreino.class));
    }

    @Test
    @DisplayName("US25 / RN-014: Bloquear reutilização de rotina para aluno inativo")
    void US25_RN014_bloquearReutilizacaoAlunoInativo() {
        Aluno alunoInativo = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(Usuario.builder().nome("Aluno Inativo").build())
                .personal(personalTrainer)
                .status(com.gymnasion.tcc.domain.enums.StatusConvite.INATIVO)
                .build();

        ReutilizarRotinaRequestDTO dto = new ReutilizarRotinaRequestDTO(
                alunoInativo.getId(),
                LocalDate.of(2026, 11, 2),
                null,
                null
        );

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));
        when(alunoRepository.findByIdAndPersonalId(alunoInativo.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(alunoInativo));

        assertThatThrownBy(() -> rotinaTreinoService.reutilizarRotina(usuarioPersonal, rotinaTreino.getId(), dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro. Este aluno está inativo.");

        verify(rotinaTreinoRepository, never()).save(any(RotinaTreino.class));
    }

    @Test
    @DisplayName("US25 Cenário 8: Encerrar rotina com pendências do personal é permitido e preserva sessões concluídas")
    void US25_cenario8_encerrarComPendenciasPermitido() {
        // Dado: sessão concluída com pendências e sessão planejada
        SessaoTreino sessaoConcluidaComPendencia = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .status(StatusSessaoTreino.CONCLUIDA)
                .dataReal(LocalDate.of(2026, 10, 1))
                .build();

        SessaoTreino sessaoPlanejada = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(2)
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        rotinaTreino.setSessoes(new ArrayList<>(List.of(sessaoConcluidaComPendencia, sessaoPlanejada)));
        rotinaTreino.setAtivo(true);

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));
        when(rotinaTreinoRepository.save(any(RotinaTreino.class))).thenAnswer(i -> i.getArgument(0));

        // Quando: personal encerra a rotina
        RotinaTreinoResponseDTO response = rotinaTreinoService.encerrarRotina(usuarioPersonal, rotinaTreino.getId());

        // Então: encerramento é permitido, status fica inativo, sessão concluída é preservada
        assertThat(response.ativo()).isFalse();
        assertThat(rotinaTreino.getSessoes()).containsExactly(sessaoConcluidaComPendencia);
    }

    @Test
    @DisplayName("US25: Tentar encerrar rotina que já está encerrada deve lançar erro")
    void US25_encerrarRotinaJaEncerradaBloqueado() {
        rotinaTreino.setAtivo(false);

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotinaTreino.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(rotinaTreino));

        assertThatThrownBy(() -> rotinaTreinoService.encerrarRotina(usuarioPersonal, rotinaTreino.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esta rotina de treino já está encerrada.");

        verify(rotinaTreinoRepository, never()).save(any(RotinaTreino.class));
    }


    // =========================================================================
    // US-13: Registro de Desempenho do Aluno (Yasmin Rezende / RN-015)
    // =========================================================================

    @Test
    @DisplayName("US13 Cenário 1: Salvar desempenho do aluno pelo personal trainer registrando data e lançador")
    void US13_cenario1_salvarDesempenhoPeloPersonalComDataELancador() {
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(metricaRepository.findById(metrica1.getId())).thenReturn(Optional.of(metrica1));
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(i -> i.getArgument(0));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                50,
                8,
                aluno.getId(),
                List.of(new RegistrarResultadoMetricaDTO(metrica1.getId(), "00:32"))
        );

        SessaoTreinoResponseDTO response = rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto);

        assertThat(response.status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
        assertThat(response.desempenho()).isNotNull();
        assertThat(response.desempenho().registradoPor()).isEqualTo("PERSONAL_TRAINER");
        assertThat(response.desempenho().alunoId()).isEqualTo(aluno.getId());
        assertThat(response.desempenho().dataRegistro()).isNotNull();
        assertThat(response.desempenho().metricas()).hasSize(1);
        assertThat(response.desempenho().metricas().get(0).valor()).isEqualTo("00:32");
    }

    @Test
    @DisplayName("US13 Cenário 2: Validar valor conforme o tipo da métrica (RN-015) impede salvamento com mensagem esperada")
    void US13_cenario2_validarValorConformeTipoMetrica_invalidoImpedeSalvamento() {
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(metricaRepository.findById(metrica2.getId())).thenReturn(Optional.of(metrica2)); // NUMERICO

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                50,
                null,
                aluno.getId(),
                List.of(new RegistrarResultadoMetricaDTO(metrica2.getId(), "valor-invalido"))
        );

        // Quando & Então: valor incompatível com tipo cadastrado impede salvamento e exibe mensagem de formato esperado
        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(MetricasInvalidasException.class)
                .hasMessageContaining("Valor inválido para a métrica 'Braçadas'")
                .hasMessageContaining("Verifique o formato esperado");

        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US13 Cenário 2: Validar tipos de métrica da RN-015 aceita todos os formatos válidos")
    void US13_cenario2_validarTiposMetricaRN015_aceitaValoresValidos() {
        Metrica fc = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("FC Repouso")
                .tipo(TipoMetrica.FREQUENCIA_CARDIACA)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .quemPreenche(ResponsavelPreenchimento.AMBOS)
                .build();

        Metrica texto = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Sensação Pós-Treino")
                .tipo(TipoMetrica.TEXTO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .quemPreenche(ResponsavelPreenchimento.AMBOS)
                .build();

        rotinaTreino.getMetricas().addAll(List.of(fc, texto));

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(metricaRepository.findById(metrica1.getId())).thenReturn(Optional.of(metrica1)); // TEMPO
        when(metricaRepository.findById(metrica2.getId())).thenReturn(Optional.of(metrica2)); // NUMERICO
        when(metricaRepository.findById(fc.getId())).thenReturn(Optional.of(fc)); // FREQUENCIA_CARDIACA
        when(metricaRepository.findById(texto.getId())).thenReturn(Optional.of(texto)); // TEXTO
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(i -> i.getArgument(0));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                null,
                aluno.getId(),
                List.of(
                        new RegistrarResultadoMetricaDTO(metrica1.getId(), "01:25"),
                        new RegistrarResultadoMetricaDTO(metrica2.getId(), "35,5"),
                        new RegistrarResultadoMetricaDTO(fc.getId(), "140"),
                        new RegistrarResultadoMetricaDTO(texto.getId(), "Excelente ritmo de prova.")
                )
        );

        SessaoTreinoResponseDTO response = rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto);

        assertThat(response.status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
        assertThat(response.desempenho().metricas()).hasSize(4);
        assertThat(response.desempenho().metricas())
                .extracting(RegistroMetricaResponseDTO::valor)
                .containsExactlyInAnyOrder("01:25", "35,5", "140", "Excelente ritmo de prova.");
    }

    @Test
    @DisplayName("US13 Cenário 5: Impedir registro sem permissão definida na US-18")
    void US13_cenario5_impedirRegistroSemPermissao() {
        // Dado: metrica exclusiva do aluno
        metrica1.setQuemPreenche(ResponsavelPreenchimento.ALUNO);

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(metricaRepository.findById(metrica1.getId())).thenReturn(Optional.of(metrica1));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                60,
                null,
                aluno.getId(),
                List.of(new RegistrarResultadoMetricaDTO(metrica1.getId(), "00:30"))
        );

        // Quando & Então: personal tenta registrar métrica exclusiva do aluno -> bloqueia
        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A métrica '" + metrica1.getTitulo() + "' é de preenchimento exclusivo do aluno.");

        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US13 / RN-014: Personal não pode registrar desempenho para aluno inativo")
    void US13_RN014_bloquearRegistroDesempenhoAlunoInativo() {
        Aluno alunoInativo = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(Usuario.builder().nome("Aluno Inativo").build())
                .personal(personalTrainer)
                .status(com.gymnasion.tcc.domain.enums.StatusConvite.INATIVO)
                .build();

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId()))
                .thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(alunoInativo.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(alunoInativo));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 20),
                50,
                null,
                alunoInativo.getId(),
                List.of(new RegistrarResultadoMetricaDTO(metrica1.getId(), "00:30"))
        );

        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro. Este aluno está inativo.");

        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    // =========================================================================
    // US-21: Visualizar rotina e treinamentos como aluno
    // =========================================================================

    @Test
    @DisplayName("US21 Cenários 1 e 2: Aluno consultando rotina ativa visualiza apenas semana atual e anterior; semanas futuras são travadas")
    void US21_cenario1_e_2_alunoConsultaRotinaAtiva_travaSemanasFuturas() {
        // Dado: rotina ativa com 4 semanas
        SessaoTreino s1 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 5))
                .status(StatusSessaoTreino.CONCLUIDA)
                .build();

        SessaoTreino s2 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(2)
                .dataPlanejada(LocalDate.of(2026, 10, 12))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        SessaoTreino s3 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(3)
                .dataPlanejada(LocalDate.of(2026, 10, 19))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        SessaoTreino s4 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(4)
                .dataPlanejada(LocalDate.of(2026, 10, 26))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        rotinaTreino.setAtivo(true);

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.findById(rotinaTreino.getId())).thenReturn(Optional.of(rotinaTreino));
        when(sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaTreino.getId()))
                .thenReturn(List.of(s1, s2, s3, s4));

        // Quando: aluno consulta o planejamento com data de referência na Semana 2
        LocalDate dataRefSemana2 = LocalDate.of(2026, 10, 14);
        PlanejamentoRotinaResponseDTO response = rotinaTreinoService.consultarPlanejamento(usuarioAluno, rotinaTreino.getId(), dataRefSemana2);

        // Então: visualização restrita à semana atual (2) e semana anterior (1); semanas futuras (3 e 4) não são exibidas
        assertThat(response.semanas()).hasSize(2);
        assertThat(response.semanas())
                .extracting(PlanejamentoRotinaResponseDTO.SemanaPlanejamentoDTO::numeroSemana)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("US21 Cenário 2: Aluno na semana 1 visualiza apenas a semana 1, travando semanas futuras")
    void US21_cenario2_alunoNaSemana1_apenasSemana1Retornada() {
        SessaoTreino s1 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 5))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        SessaoTreino s2 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(2)
                .dataPlanejada(LocalDate.of(2026, 10, 12))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        rotinaTreino.setAtivo(true);

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.findById(rotinaTreino.getId())).thenReturn(Optional.of(rotinaTreino));
        when(sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaTreino.getId()))
                .thenReturn(List.of(s1, s2));

        // Data na semana 1
        LocalDate dataRefSemana1 = LocalDate.of(2026, 10, 6);
        PlanejamentoRotinaResponseDTO response = rotinaTreinoService.consultarPlanejamento(usuarioAluno, rotinaTreino.getId(), dataRefSemana1);

        assertThat(response.semanas()).hasSize(1);
        assertThat(response.semanas().get(0).numeroSemana()).isEqualTo(1);
    }

    @Test
    @DisplayName("US21 Cenário 5: Aluno consultando rotina encerrada visualiza todas as semanas do histórico sem travar")
    void US21_cenario5_alunoConsultaRotinaEncerrada_exibeTodasAsSemanas() {
        SessaoTreino s1 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 5))
                .status(StatusSessaoTreino.CONCLUIDA)
                .build();

        SessaoTreino s2 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(2)
                .dataPlanejada(LocalDate.of(2026, 10, 12))
                .status(StatusSessaoTreino.CONCLUIDA)
                .build();

        SessaoTreino s3 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(3)
                .dataPlanejada(LocalDate.of(2026, 10, 19))
                .status(StatusSessaoTreino.CONCLUIDA)
                .build();

        // Rotina encerrada (ativo = false)
        rotinaTreino.setAtivo(false);

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.findById(rotinaTreino.getId())).thenReturn(Optional.of(rotinaTreino));
        when(sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaTreino.getId()))
                .thenReturn(List.of(s1, s2, s3));

        // Mesmo com data na semana 1, rotina encerrada disponibiliza todas as semanas para consulta histórica
        LocalDate dataRef = LocalDate.of(2026, 10, 6);
        PlanejamentoRotinaResponseDTO response = rotinaTreinoService.consultarPlanejamento(usuarioAluno, rotinaTreino.getId(), dataRef);

        assertThat(response.semanas()).hasSize(3);
        assertThat(response.semanas())
                .extracting(PlanejamentoRotinaResponseDTO.SemanaPlanejamentoDTO::numeroSemana)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("US23 / US10: Personal inclui métrica pendente em execução já registrada, com auditoria e sem alterar o registro do aluno")
    void US23_personalIncluiPendenteEmExecucaoRegistradaComAuditoria() {
        // Dado: sessão concluída pelo aluno, com métrica exclusiva do personal pendente
        Metrica exclusivaPersonal = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Análise Técnica")
                .tipo(TipoMetrica.TEXTO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .quemPreenche(ResponsavelPreenchimento.PERSONAL)
                .build();
        rotinaTreino.getMetricas().add(exclusivaPersonal);

        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .dataReal(LocalDate.of(2026, 10, 22))
                .duracao(55)
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .build();
        Desempenho desempenho = Desempenho.builder()
                .id(UUID.randomUUID())
                .sessaoTreino(sessao)
                .aluno(aluno)
                .registradoPor("ALUNO")
                .nivelCansaco(7)
                .build();
        desempenho.getRegistrosMetricas().add(RegistroMetrica.builder()
                .desempenho(desempenho).metrica(metrica1).valor("00:29").build());
        sessao.setDesempenho(desempenho);

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId())).thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));
        when(metricaRepository.findById(exclusivaPersonal.getId())).thenReturn(Optional.of(exclusivaPersonal));
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Quando: personal envia a métrica pendente com outros dados de execução
        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 25),
                90,
                3,
                aluno.getId(),
                List.of(new RegistrarResultadoMetricaDTO(exclusivaPersonal.getId(), "Boa técnica"))
        );
        SessaoTreinoResponseDTO response = rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto);

        // Então: a métrica é incluída e o registro original do aluno é preservado
        assertThat(response.dataReal()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(response.duracao()).isEqualTo(55);
        assertThat(response.desempenho().nivelCansaco()).isEqualTo(7);
        assertThat(response.desempenho().registradoPor()).isEqualTo("ALUNO");
        assertThat(response.desempenho().metricas())
                .extracting(RegistroMetricaResponseDTO::valor)
                .containsExactlyInAnyOrder("00:29", "Boa técnica");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditoriaDesempenho>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaDesempenhoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getTipoOperacao()).isEqualTo(TipoOperacaoAuditoria.INCLUSAO);
            assertThat(auditoria.getCampo()).isEqualTo(CampoAuditado.METRICA);
            assertThat(auditoria.getValorNovo()).isEqualTo("Boa técnica");
            assertThat(auditoria.getUsuario()).isSameAs(usuarioPersonal);
        });
    }

    @Test
    @DisplayName("US23 / US10: Personal não sobrescreve métrica já preenchida pelo endpoint de execução")
    void US23_personalNaoSobrescreveMetricaPreenchidaPelaExecucao() {
        // Dado: sessão concluída com métrica já preenchida
        metrica2.setQuemPreenche(ResponsavelPreenchimento.AMBOS);
        UUID sessaoId = UUID.randomUUID();
        SessaoTreino sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .dataReal(LocalDate.of(2026, 10, 22))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .build();
        Desempenho desempenho = Desempenho.builder()
                .id(UUID.randomUUID())
                .sessaoTreino(sessao)
                .aluno(aluno)
                .registradoPor("ALUNO")
                .build();
        desempenho.getRegistrosMetricas().add(RegistroMetrica.builder()
                .desempenho(desempenho).metrica(metrica2).valor("30").build());
        sessao.setDesempenho(desempenho);

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId())).thenReturn(Optional.of(sessao));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));
        when(metricaRepository.findById(metrica2.getId())).thenReturn(Optional.of(metrica2));

        RegistrarExecucaoSessaoRequestDTO dto = new RegistrarExecucaoSessaoRequestDTO(
                LocalDate.of(2026, 10, 22),
                null,
                null,
                aluno.getId(),
                List.of(new RegistrarResultadoMetricaDTO(metrica2.getId(), "40"))
        );

        // Quando & Então: a alteração é recusada e deve ser feita pela correção (US-23)
        assertThatThrownBy(() -> rotinaTreinoService.registrarExecucao(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A métrica 'Braçadas' já possui valor registrado. Para alterá-la, utilize a correção do registro de desempenho.");

        assertThat(desempenho.getRegistrosMetricas().get(0).getValor()).isEqualTo("30");
        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
        verify(auditoriaDesempenhoRepository, never()).saveAll(anyList());
    }
}
