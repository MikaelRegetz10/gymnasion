package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.*;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.PontoMetricaDashboardDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.SerieMetricaDashboardDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.DesempenhoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardEvolucaoServiceTest {

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;

    @Mock
    private DesempenhoRepository desempenhoRepository;

    @InjectMocks
    private DashboardEvolucaoService dashboardEvolucaoService;

    private Usuario usuarioPersonal;
    private Usuario usuarioAluno;
    private PersonalTrainer personalTrainer;
    private Aluno aluno;
    private Modalidade modalidade;
    private RotinaTreino rotinaTreino;
    private Metrica metricaCarga;
    private Metrica metricaTempo;
    private Metrica metricaDescontinuada;

    @BeforeEach
    void setUp() {
        usuarioPersonal = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Treinador Lucas")
                .email("lucas@gymnasion.com")
                .role(Role.PERSONAL_TRAINER)
                .build();

        personalTrainer = PersonalTrainer.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioPersonal)
                .build();

        usuarioAluno = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Yasmin Rezende")
                .email("yasmin@aluno.com")
                .role(Role.ALUNO)
                .build();

        modalidade = Modalidade.builder()
                .id(1L)
                .nome("Musculação")
                .build();

        aluno = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .personal(personalTrainer)
                .status(StatusConvite.ATIVO)
                .modalidades(new HashSet<>(Set.of(modalidade)))
                .build();

        rotinaTreino = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Treino Força A")
                .personalTrainer(personalTrainer)
                .modalidade(modalidade)
                .alunos(new HashSet<>(Set.of(aluno)))
                .build();

        metricaCarga = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Carga Supino")
                .tipo(TipoMetrica.NUMERICO)
                .obrigatoria(true)
                .quemPreenche(ResponsavelPreenchimento.AMBOS)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        metricaTempo = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Tempo Corrida 1km")
                .tipo(TipoMetrica.TEMPO)
                .obrigatoria(false)
                .quemPreenche(ResponsavelPreenchimento.ALUNO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        metricaDescontinuada = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Flexões Antigas")
                .tipo(TipoMetrica.NUMERICO)
                .obrigatoria(false)
                .quemPreenche(ResponsavelPreenchimento.ALUNO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(false) // RN-017
                .build();
    }

    private Desempenho criarDesempenho(LocalDate data, Integer nivelCansaco, List<RegistroMetrica> metricas) {
        SessaoTreino sessao = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .dataPlanejada(data)
                .dataReal(data)
                .duracao(60)
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .build();

        Desempenho d = Desempenho.builder()
                .id(UUID.randomUUID())
                .sessaoTreino(sessao)
                .aluno(aluno)
                .dataRegistro(OffsetDateTime.now())
                .nivelCansaco(nivelCansaco)
                .registrosMetricas(new ArrayList<>(metricas != null ? metricas : List.of()))
                .build();

        sessao.setDesempenho(d);
        if (metricas != null) {
            metricas.forEach(rm -> rm.setDesempenho(d));
        }
        return d;
    }

    @Test
    @DisplayName("US15 Cenário 1: Painel com ao menos 3 sessões exibe gráficos de linha e séries temporais com dados suficientes")
    void US15_cenario1_visualizacaoEvolucao_comAoMenosTresSessoesRegistradas_retornaGraficosEMetricas() {
        RegistroMetrica rm1 = RegistroMetrica.builder().metrica(metricaCarga).valor("50.0").build();
        Desempenho d1 = criarDesempenho(LocalDate.of(2026, 9, 1), 5, List.of(rm1));

        RegistroMetrica rm2 = RegistroMetrica.builder().metrica(metricaCarga).valor("55.0").build();
        Desempenho d2 = criarDesempenho(LocalDate.of(2026, 9, 8), 6, List.of(rm2));

        RegistroMetrica rm3 = RegistroMetrica.builder().metrica(metricaCarga).valor("60.0").build();
        Desempenho d3 = criarDesempenho(LocalDate.of(2026, 9, 15), 7, List.of(rm3));

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));
        when(desempenhoRepository.findByAlunoIdWithMetricas(aluno.getId())).thenReturn(List.of(d1, d2, d3));

        DashboardEvolucaoResponseDTO response = dashboardEvolucaoService.obterDashboardPeloPersonal(
                usuarioPersonal, aluno.getId(), null, null, null
        );

        assertThat(response).isNotNull();
        assertThat(response.possuiDadosSuficientes()).isTrue();
        assertThat(response.mensagem()).isNull();
        assertThat(response.resumoGeral().totalSessoesHistorico()).isEqualTo(3);
        assertThat(response.metricas()).hasSize(1);

        SerieMetricaDashboardDTO serieCarga = response.metricas().get(0);
        assertThat(serieCarga.titulo()).isEqualTo("Carga Supino");
        assertThat(serieCarga.pontos()).hasSize(3);
        assertThat(serieCarga.tendencia().direcao()).isEqualTo("AUMENTO");
        assertThat(serieCarga.tendencia().indicadorVisual()).isEqualTo("MELHORIA");
        assertThat(serieCarga.tendencia().percentualVariacao()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("US15 Cenário 1: Menos de 3 sessões registradas informa necessidade de ao menos 3 sessões")
    void US15_cenario1_visualizacaoEvolucao_comMenosDeTresSessoes_indicaDadosInsuficientes() {
        RegistroMetrica rm1 = RegistroMetrica.builder().metrica(metricaCarga).valor("50.0").build();
        Desempenho d1 = criarDesempenho(LocalDate.of(2026, 9, 1), 5, List.of(rm1));

        RegistroMetrica rm2 = RegistroMetrica.builder().metrica(metricaCarga).valor("55.0").build();
        Desempenho d2 = criarDesempenho(LocalDate.of(2026, 9, 8), 6, List.of(rm2));

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));
        when(desempenhoRepository.findByAlunoIdWithMetricas(aluno.getId())).thenReturn(List.of(d1, d2));

        DashboardEvolucaoResponseDTO response = dashboardEvolucaoService.obterDashboardPeloPersonal(
                usuarioPersonal, aluno.getId(), null, null, null
        );

        assertThat(response.possuiDadosSuficientes()).isFalse();
        assertThat(response.mensagem()).isEqualTo("São necessárias pelo menos 3 sessões registradas para exibir os gráficos de evolução.");
    }

    @Test
    @DisplayName("US15 Cenário 2: Filtro de período aplicado restringe pontos das métricas ao intervalo selecionado")
    void US15_cenario2_visualizacaoPorFiltro_aplicaFiltroDePeriodoComSucesso() {
        RegistroMetrica rm1 = RegistroMetrica.builder().metrica(metricaCarga).valor("40.0").build();
        Desempenho d1 = criarDesempenho(LocalDate.of(2026, 8, 1), 4, List.of(rm1));

        RegistroMetrica rm2 = RegistroMetrica.builder().metrica(metricaCarga).valor("50.0").build();
        Desempenho d2 = criarDesempenho(LocalDate.of(2026, 9, 5), 6, List.of(rm2));

        RegistroMetrica rm3 = RegistroMetrica.builder().metrica(metricaCarga).valor("60.0").build();
        Desempenho d3 = criarDesempenho(LocalDate.of(2026, 9, 20), 7, List.of(rm3));

        RegistroMetrica rm4 = RegistroMetrica.builder().metrica(metricaCarga).valor("70.0").build();
        Desempenho d4 = criarDesempenho(LocalDate.of(2026, 10, 5), 8, List.of(rm4));

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));
        when(desempenhoRepository.findByAlunoIdWithMetricas(aluno.getId())).thenReturn(List.of(d1, d2, d3, d4));

        LocalDate inicio = LocalDate.of(2026, 9, 1);
        LocalDate fim = LocalDate.of(2026, 9, 30);

        DashboardEvolucaoResponseDTO response = dashboardEvolucaoService.obterDashboardPeloPersonal(
                usuarioPersonal, aluno.getId(), null, inicio, fim
        );

        assertThat(response.resumoGeral().totalSessoesHistorico()).isEqualTo(4);
        assertThat(response.resumoGeral().totalSessoesPeriodo()).isEqualTo(2);

        SerieMetricaDashboardDTO serie = response.metricas().get(0);
        assertThat(serie.pontos()).hasSize(2);
        assertThat(serie.pontos().get(0).data()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(serie.pontos().get(1).data()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    @DisplayName("US15 Cenário 2: Data de início posterior à data de fim lança BusinessException")
    void US15_cenario2_filtroPeriodoInvalido_dataInicioPosteriorADataFim_lancaBusinessException() {
        LocalDate inicio = LocalDate.of(2026, 10, 10);
        LocalDate fim = LocalDate.of(2026, 10, 5);

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> dashboardEvolucaoService.obterDashboardPeloPersonal(
                usuarioPersonal, aluno.getId(), null, inicio, fim
        )).isInstanceOf(BusinessException.class)
                .hasMessage("A data de início não pode ser posterior à data de fim.");
    }

    @Test
    @DisplayName("US15 Cenário 3: Aluno autenticado acessa seu próprio dashboard com indicadores de melhoria/regressão")
    void US15_cenario3_visualizacaoPropriaMetricaAluno_alunoAcessaProprioDashboardComSucesso() {
        // Métrica de tempo: redução de tempo é MELHORIA (05:00 para 04:30)
        RegistroMetrica rm1 = RegistroMetrica.builder().metrica(metricaTempo).valor("05:00").build();
        Desempenho d1 = criarDesempenho(LocalDate.of(2026, 9, 1), 6, List.of(rm1));

        RegistroMetrica rm2 = RegistroMetrica.builder().metrica(metricaTempo).valor("04:45").build();
        Desempenho d2 = criarDesempenho(LocalDate.of(2026, 9, 8), 6, List.of(rm2));

        RegistroMetrica rm3 = RegistroMetrica.builder().metrica(metricaTempo).valor("04:30").build();
        Desempenho d3 = criarDesempenho(LocalDate.of(2026, 9, 15), 5, List.of(rm3));

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(desempenhoRepository.findByAlunoIdWithMetricas(aluno.getId())).thenReturn(List.of(d1, d2, d3));

        DashboardEvolucaoResponseDTO response = dashboardEvolucaoService.obterDashboardPeloAluno(
                usuarioAluno, null, null, null
        );

        assertThat(response).isNotNull();
        assertThat(response.aluno().nome()).isEqualTo("Yasmin Rezende");
        assertThat(response.metricas()).hasSize(1);

        SerieMetricaDashboardDTO serie = response.metricas().get(0);
        assertThat(serie.tendencia().direcao()).isEqualTo("REDUCAO");
        assertThat(serie.tendencia().indicadorVisual()).isEqualTo("MELHORIA"); // Redução de tempo é melhoria
        assertThat(serie.pontos().get(0).valorNumerico()).isEqualTo(300.0); // 5 min = 300 s
        assertThat(serie.pontos().get(2).valorNumerico()).isEqualTo(270.0); // 4 min 30 s = 270 s
    }

    @Test
    @DisplayName("US15 / RN-014: Aluno inativo tenta acessar dashboard e recebe erro de inatividade")
    void US15_RN014_alunoInativoTentaAcessarDashboard_lancaBusinessException() {
        aluno.setStatus(StatusConvite.INATIVO);

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> dashboardEvolucaoService.obterDashboardPeloAluno(
                usuarioAluno, null, null, null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Erro. Este aluno está inativo.");
    }

    @Test
    @DisplayName("US15 / RN-017: Métrica descontinuada continua sendo exibida no dashboard sem quebrar séries")
    void US15_RN017_metricaDescontinuada_permaneceVisivelComStatusDescontinuada() {
        RegistroMetrica rm1 = RegistroMetrica.builder().metrica(metricaDescontinuada).valor("20").build();
        Desempenho d1 = criarDesempenho(LocalDate.of(2026, 9, 1), 6, List.of(rm1));

        RegistroMetrica rm2 = RegistroMetrica.builder().metrica(metricaDescontinuada).valor("25").build();
        Desempenho d2 = criarDesempenho(LocalDate.of(2026, 9, 8), 6, List.of(rm2));

        RegistroMetrica rm3 = RegistroMetrica.builder().metrica(metricaDescontinuada).valor("30").build();
        Desempenho d3 = criarDesempenho(LocalDate.of(2026, 9, 15), 7, List.of(rm3));

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));
        when(desempenhoRepository.findByAlunoIdWithMetricas(aluno.getId())).thenReturn(List.of(d1, d2, d3));

        DashboardEvolucaoResponseDTO response = dashboardEvolucaoService.obterDashboardPeloPersonal(
                usuarioPersonal, aluno.getId(), null, null, null
        );

        assertThat(response.metricas()).hasSize(1);
        SerieMetricaDashboardDTO serie = response.metricas().get(0);
        assertThat(serie.descontinuada()).isTrue();
        assertThat(serie.statusMetrica()).isEqualTo("métrica descontinuada");
        assertThat(serie.pontos()).hasSize(3);
    }

    @Test
    @DisplayName("US20 Cenário 3 / RN-018: Aluno com ao menos 3 sessões com RPE exibe tendência de esforço Borg CR-10")
    void US20_cenario3_evolucaoRpe_comAoMenosTresSessoesComRpe_retornaTendenciaEMedia() {
        Desempenho d1 = criarDesempenho(LocalDate.of(2026, 9, 1), 4, null);
        Desempenho d2 = criarDesempenho(LocalDate.of(2026, 9, 8), 6, null);
        Desempenho d3 = criarDesempenho(LocalDate.of(2026, 9, 15), 8, null);

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));
        when(desempenhoRepository.findByAlunoIdWithMetricas(aluno.getId())).thenReturn(List.of(d1, d2, d3));

        DashboardEvolucaoResponseDTO response = dashboardEvolucaoService.obterDashboardPeloPersonal(
                usuarioPersonal, aluno.getId(), null, null, null
        );

        assertThat(response.evolucaoRpe().possuiDadosSuficientes()).isTrue();
        assertThat(response.evolucaoRpe().mensagem()).isNull();
        assertThat(response.evolucaoRpe().totalSessoesComRpe()).isEqualTo(3);
        assertThat(response.evolucaoRpe().mediaRpe()).isEqualTo(6.0);
        assertThat(response.evolucaoRpe().ultimoRpe()).isEqualTo(8);
        assertThat(response.evolucaoRpe().direcaoTendencia()).isEqualTo("AUMENTO");
        assertThat(response.evolucaoRpe().pontos().get(0).descricao()).isEqualTo("Borg 4 - Moderado");
        assertThat(response.evolucaoRpe().pontos().get(2).descricao()).isEqualTo("Borg 8 - Muito forte +");
    }

    @Test
    @DisplayName("US20 Cenários 2 e 3: RPE não informado aparece como 'não informado' e indica dados insuficientes se < 3")
    void US20_cenarios2_e_3_evolucaoRpe_rpeNaoInformadoExibeComoNaoInformadoEValidaLimiar() {
        Desempenho d1 = criarDesempenho(LocalDate.of(2026, 9, 1), 6, null);
        Desempenho d2 = criarDesempenho(LocalDate.of(2026, 9, 8), null, null); // pulou RPE (US-20 C2)
        Desempenho d3 = criarDesempenho(LocalDate.of(2026, 9, 15), 7, null);

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));
        when(desempenhoRepository.findByAlunoIdWithMetricas(aluno.getId())).thenReturn(List.of(d1, d2, d3));

        DashboardEvolucaoResponseDTO response = dashboardEvolucaoService.obterDashboardPeloPersonal(
                usuarioPersonal, aluno.getId(), null, null, null
        );

        // Sessões totais = 3 (possuiDadosSuficientes = true para o dashboard)
        assertThat(response.possuiDadosSuficientes()).isTrue();
        // Apenas 2 com RPE -> possuiDadosSuficientesRpe = false
        assertThat(response.evolucaoRpe().possuiDadosSuficientes()).isFalse();
        assertThat(response.evolucaoRpe().mensagem()).isEqualTo("São necessárias pelo menos 3 sessões com nível de cansaço (RPE) registrado para exibir a tendência de esforço.");
        assertThat(response.evolucaoRpe().pontos().get(1).descricao()).isEqualTo("Não informado");
    }

    @Test
    @DisplayName("US15: Personal trainer tenta consultar aluno de outro personal e recebe NotFoundException")
    void US15_personalConsultaAlunoDeOutroTreinador_lancaNotFoundException() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardEvolucaoService.obterDashboardPeloPersonal(
                usuarioPersonal, aluno.getId(), null, null, null
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Aluno não encontrado para este Personal Trainer.");
    }
}
