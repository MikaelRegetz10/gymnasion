package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.MetricasInvalidasException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}

