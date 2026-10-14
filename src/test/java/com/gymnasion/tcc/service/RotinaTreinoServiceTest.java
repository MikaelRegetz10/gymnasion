package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.BusinessException;
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
}
