package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.*;
import com.gymnasion.tcc.dto.IntervaloRegistrosModalidadeDTO;
import com.gymnasion.tcc.dto.RelatorioPdfDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.exceptions.RelatorioSemDadosException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.DesempenhoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioPerformanceServiceTest {

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;

    @Mock
    private DesempenhoRepository desempenhoRepository;

    @Spy
    private RelatorioPdfGenerator relatorioPdfGenerator;

    @InjectMocks
    private RelatorioPerformanceService relatorioPerformanceService;

    private Usuario usuarioPersonal;
    private Usuario usuarioAluno;
    private PersonalTrainer personalTrainer;
    private Aluno aluno;
    private Modalidade modalidade;
    private RotinaTreino rotinaTreino;
    private Metrica metricaCarga;
    private Metrica metricaTempo;
    private Metrica metricaDescontinuada;

    private final LocalDate inicio = LocalDate.of(2026, 3, 1);
    private final LocalDate fim = LocalDate.of(2026, 3, 31);

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
                .nome("Natação")
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
                .titulo("Treino Base")
                .personalTrainer(personalTrainer)
                .modalidade(modalidade)
                .alunos(new HashSet<>(Set.of(aluno)))
                .build();

        metricaCarga = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Distância Percorrida")
                .tipo(TipoMetrica.NUMERICO)
                .obrigatoria(true)
                .quemPreenche(ResponsavelPreenchimento.AMBOS)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        metricaTempo = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Tempo 100m")
                .tipo(TipoMetrica.TEMPO)
                .obrigatoria(false)
                .quemPreenche(ResponsavelPreenchimento.ALUNO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        metricaDescontinuada = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Braçadas Antigas")
                .tipo(TipoMetrica.NUMERICO)
                .obrigatoria(false)
                .quemPreenche(ResponsavelPreenchimento.ALUNO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(false) // RN-017
                .build();
    }

    private Desempenho criarDesempenho(LocalDate dataPlanejada, LocalDate dataReal, Integer nivelCansaco, List<RegistroMetrica> metricas) {
        SessaoTreino sessao = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .dataPlanejada(dataPlanejada)
                .dataReal(dataReal)
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

    private RegistroMetrica registro(Metrica metrica, String valor) {
        return RegistroMetrica.builder().metrica(metrica).valor(valor).build();
    }

    private List<Desempenho> historicoPadrao() {
        return List.of(
                criarDesempenho(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), 6,
                        List.of(registro(metricaCarga, "1500"), registro(metricaTempo, "01:45"), registro(metricaDescontinuada, "30"))),
                criarDesempenho(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10), null,
                        List.of(registro(metricaCarga, "1800"), registro(metricaTempo, "01:40"))),
                criarDesempenho(LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 16), 7,
                        List.of(registro(metricaCarga, "2000"), registro(metricaTempo, "01:38")))
        );
    }

    private void mockPersonalComAluno() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId())).thenReturn(Optional.of(aluno));
    }

    private String extrairTexto(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder texto = new StringBuilder();
        for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
            texto.append(extractor.getTextFromPage(pagina)).append('\n');
        }
        reader.close();
        return texto.toString();
    }

    @Test
    @DisplayName("US16 Cenário 1: Personal gera relatório de aluno com histórico e recebe PDF com dados tabulados e gráficos")
    void US16_cenario1_gerarRelatorio_comHistorico_retornaPdfComDadosTabulados() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, inicio, fim);

        assertThat(new String(relatorio.conteudo(), 0, 5)).isEqualTo("%PDF-");
        assertThat(relatorio.nomeArquivo()).isEqualTo("relatorio-performance-yasmin-rezende-2026-03-01_2026-03-31.pdf");

        String texto = extrairTexto(relatorio.conteudo());
        assertThat(texto)
                .contains("RELATÓRIO DE PERFORMANCE")
                .contains("Yasmin Rezende")
                .contains("Treinador Lucas")
                .contains("01/03/2026 – 31/03/2026")
                .contains("Natação")
                .contains("Resumo do período")
                .contains("Evolução")
                .contains("Distância Percorrida")
                .contains("Tempo 100m")
                .contains("Valores registrados")
                .contains("2.000")
                .contains("1:38");
    }

    @Test
    @DisplayName("US16 Cenário 1: Relatório mantém a data planejada e a data real como colunas separadas")
    void US16_cenario1_relatorio_mantemDataPlanejadaEDataRealSeparadas() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, inicio, fim);

        String texto = extrairTexto(relatorio.conteudo());
        assertThat(texto).contains("Data planejada").contains("Data real").contains("09/03/2026").contains("10/03/2026");
    }

    @Test
    @DisplayName("US16 Cenário 1: Aluno ativo gera o próprio relatório")
    void US16_cenario1_alunoGeraProprioRelatorio_retornaPdf() throws IOException {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloAluno(usuarioAluno, null, inicio, fim);

        assertThat(extrairTexto(relatorio.conteudo())).contains("Yasmin Rezende").contains("Distância Percorrida");
    }

    @Test
    @DisplayName("US16 Cenário 3: Período sem dados informa a mensagem e sugere intervalo alternativo com registros")
    void US16_cenario3_periodoSemDados_lancaExcecaoComPeriodoSugerido() {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(List.of());
        when(desempenhoRepository.findIntervalosRegistrosByAlunoId(aluno.getId())).thenReturn(List.of(
                new IntervaloRegistrosModalidadeDTO(1L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 20)),
                new IntervaloRegistrosModalidadeDTO(2L, LocalDate.of(2025, 11, 1), LocalDate.of(2026, 1, 10))
        ));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, inicio, fim))
                .isInstanceOf(RelatorioSemDadosException.class)
                .hasMessage("Não há dados para o período selecionado")
                .satisfies(ex -> {
                    RelatorioSemDadosException semDados = (RelatorioSemDadosException) ex;
                    assertThat(semDados.getDataInicioSugerida()).isEqualTo(LocalDate.of(2025, 11, 1));
                    assertThat(semDados.getDataFimSugerida()).isEqualTo(LocalDate.of(2026, 2, 20));
                });
        verify(relatorioPdfGenerator, never()).gerar(any());
    }

    @Test
    @DisplayName("US16 Cenário 3: Com filtro de modalidade, o intervalo sugerido considera apenas os registros da modalidade")
    void US16_cenario3_periodoSemDadosComModalidade_sugereIntervaloDaModalidade() {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(List.of());
        when(desempenhoRepository.findIntervalosRegistrosByAlunoId(aluno.getId())).thenReturn(List.of(
                new IntervaloRegistrosModalidadeDTO(1L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 20)),
                new IntervaloRegistrosModalidadeDTO(2L, LocalDate.of(2025, 11, 1), LocalDate.of(2026, 1, 10))
        ));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), 1L, inicio, fim))
                .isInstanceOf(RelatorioSemDadosException.class)
                .satisfies(ex -> {
                    RelatorioSemDadosException semDados = (RelatorioSemDadosException) ex;
                    assertThat(semDados.getDataInicioSugerida()).isEqualTo(LocalDate.of(2026, 1, 5));
                    assertThat(semDados.getDataFimSugerida()).isEqualTo(LocalDate.of(2026, 2, 20));
                });
    }

    @Test
    @DisplayName("US16 Cenário 3: Aluno sem nenhum registro recebe a mensagem sem período sugerido")
    void US16_cenario3_alunoSemNenhumRegistro_lancaExcecaoSemPeriodoSugerido() {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(List.of());
        when(desempenhoRepository.findIntervalosRegistrosByAlunoId(aluno.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, inicio, fim))
                .isInstanceOf(RelatorioSemDadosException.class)
                .hasMessage("Não há dados para o período selecionado")
                .satisfies(ex -> assertThat(((RelatorioSemDadosException) ex).possuiPeriodoSugerido()).isFalse());
    }

    @Test
    @DisplayName("US16 RN-006: Personal não emite relatório de aluno de outro personal")
    void US16_RN006_personalDeOutroAluno_lancaNotFound() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        UUID alunoDeOutroPersonal = UUID.randomUUID();
        when(alunoRepository.findByIdAndPersonalId(alunoDeOutroPersonal, personalTrainer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, alunoDeOutroPersonal, null, inicio, fim))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Aluno não encontrado para este Personal Trainer.");
    }

    @Test
    @DisplayName("US16 RN-014: Aluno inativo não emite o próprio relatório")
    void US16_RN014_alunoInativo_bloqueadoParaAluno() {
        aluno.setStatus(StatusConvite.INATIVO);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloAluno(usuarioAluno, null, inicio, fim))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro. Este aluno está inativo.");
    }

    @Test
    @DisplayName("US16 RN-014: Histórico do aluno inativo é preservado e o personal ainda emite o relatório")
    void US16_RN014_alunoInativo_personalEmiteRelatorioDoHistorico() throws IOException {
        aluno.setStatus(StatusConvite.INATIVO);
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, inicio, fim);

        assertThat(extrairTexto(relatorio.conteudo())).contains("Yasmin Rezende");
    }

    @Test
    @DisplayName("US16 RN-017: Métrica descontinuada aparece no relatório identificada como descontinuada")
    void US16_RN017_metricaDescontinuada_apareceMarcadaNoRelatorio() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, inicio, fim);

        assertThat(extrairTexto(relatorio.conteudo())).contains("Braçadas Antigas").contains("métrica descontinuada");
    }

    @Test
    @DisplayName("US16 RN-018 / US20 C2: RPE pulado aparece como não informado no relatório")
    void US16_RN018_rpeNaoInformado_apareceComoNaoInformado() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, inicio, fim);

        assertThat(extrairTexto(relatorio.conteudo())).contains("não informado").contains("Nível de esforço (RPE)").contains("6,5");
    }

    @Test
    @DisplayName("US16: Período não informado impede a geração do relatório")
    void US16_periodoNaoInformado_lancaBusinessException() {
        mockPersonalComAluno();

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, null, fim))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe a data de início e a data de fim do período do relatório.");
    }

    @Test
    @DisplayName("US16: Data de início posterior à data de fim impede a geração do relatório")
    void US16_periodoInvalido_dataInicioPosteriorAFim_lancaBusinessException() {
        mockPersonalComAluno();

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, fim, inicio))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A data de início não pode ser posterior à data de fim.");
    }

    @Test
    @DisplayName("US16: Modalidade que o aluno não pratica e sem histórico retorna não encontrada")
    void US16_modalidadeInexistenteParaAluno_lancaNotFound() {
        mockPersonalComAluno();
        when(desempenhoRepository.findIntervalosRegistrosByAlunoId(aluno.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), 99L, inicio, fim))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Modalidade não encontrada para este aluno.");
    }

    @Test
    @DisplayName("US16 RNF-004: Relatório com 10 mil registros é gerado em até 40 segundos")
    void US16_RNF004_desempenho_10milRegistros_geraEmMenosDe40s() {
        mockPersonalComAluno();
        LocalDate inicioAmplo = LocalDate.of(2000, 1, 1);
        LocalDate fimAmplo = LocalDate.of(2030, 12, 31);

        List<Desempenho> desempenhos = new ArrayList<>();
        LocalDate data = LocalDate.of(2001, 1, 1);
        Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            desempenhos.add(criarDesempenho(data, data, 1 + random.nextInt(10), List.of(
                    registro(metricaCarga, String.valueOf(1000 + random.nextInt(2000))),
                    registro(metricaTempo, String.format("%02d:%02d", 1 + random.nextInt(2), random.nextInt(60)))
            )));
            data = data.plusDays(1);
        }
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicioAmplo, fimAmplo)).thenReturn(desempenhos);

        long inicioExecucao = System.nanoTime();
        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), null, inicioAmplo, fimAmplo);
        Duration tempo = Duration.ofNanos(System.nanoTime() - inicioExecucao);

        assertThat(relatorio.conteudo()).isNotEmpty();
        assertThat(tempo).isLessThan(Duration.ofSeconds(40));
    }
}
