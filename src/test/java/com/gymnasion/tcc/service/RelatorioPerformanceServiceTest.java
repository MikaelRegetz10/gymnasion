package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.*;
import com.gymnasion.tcc.dto.DiaRegistrosMetricaDTO;
import com.gymnasion.tcc.dto.DiaRegistrosModalidadeDTO;
import com.gymnasion.tcc.dto.DisponibilidadeRelatorioDTO;
import com.gymnasion.tcc.dto.DisponibilidadeRelatorioDTO.ModalidadeDisponivelDTO;
import com.gymnasion.tcc.dto.FiltroRelatorioDTO;
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

    private FiltroRelatorioDTO filtro(Long modalidadeId, LocalDate dataInicio, LocalDate dataFim) {
        return new FiltroRelatorioDTO(modalidadeId, dataInicio, dataFim, null, null, null);
    }

    private FiltroRelatorioDTO filtroMetricas(Metrica... metricas) {
        Set<UUID> ids = new HashSet<>();
        Arrays.stream(metricas).forEach(m -> ids.add(m.getId()));
        return new FiltroRelatorioDTO(null, inicio, fim, null, ids, null);
    }

    private FiltroRelatorioDTO filtroConteudos(ConteudoRelatorio... conteudos) {
        return new FiltroRelatorioDTO(null, inicio, fim, null, null, Set.of(conteudos));
    }

    private DiaRegistrosModalidadeDTO dia(Modalidade m, LocalDate data, long sessoes, long comRpe) {
        return new DiaRegistrosModalidadeDTO(m.getId(), m.getNome(), data, sessoes, comRpe);
    }

    private DiaRegistrosMetricaDTO diaMetrica(Modalidade m, Metrica metrica, LocalDate data) {
        return new DiaRegistrosMetricaDTO(m.getId(), metrica.getId(), metrica.getTitulo(), metrica.getTipo(), metrica.getAtivo(), data, 1L);
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

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, inicio, fim));

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

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, inicio, fim));

        String texto = extrairTexto(relatorio.conteudo());
        assertThat(texto).contains("Data planejada").contains("Data real").contains("09/03/2026").contains("10/03/2026");
    }

    @Test
    @DisplayName("US16 Cenário 1: Aluno ativo gera o próprio relatório")
    void US16_cenario1_alunoGeraProprioRelatorio_retornaPdf() throws IOException {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloAluno(usuarioAluno, filtro(null, inicio, fim));

        assertThat(extrairTexto(relatorio.conteudo())).contains("Yasmin Rezende").contains("Distância Percorrida");
    }

    @Test
    @DisplayName("US16 Cenário 3: Período sem dados informa a mensagem e sugere intervalo alternativo com registros")
    void US16_cenario3_periodoSemDados_lancaExcecaoComPeriodoSugerido() {
        Modalidade tenis = Modalidade.builder().id(2L).nome("Tênis").build();
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(List.of());
        when(desempenhoRepository.findDiasRegistrosByAlunoId(aluno.getId())).thenReturn(List.of(
                dia(modalidade, LocalDate.of(2026, 1, 5), 1, 0),
                dia(modalidade, LocalDate.of(2026, 2, 20), 1, 0),
                dia(tenis, LocalDate.of(2025, 11, 1), 1, 0),
                dia(tenis, LocalDate.of(2026, 1, 10), 1, 0)
        ));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, inicio, fim)))
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
        Modalidade tenis = Modalidade.builder().id(2L).nome("Tênis").build();
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(List.of());
        when(desempenhoRepository.findDiasRegistrosByAlunoId(aluno.getId())).thenReturn(List.of(
                dia(modalidade, LocalDate.of(2026, 1, 5), 1, 0),
                dia(modalidade, LocalDate.of(2026, 2, 20), 1, 0),
                dia(tenis, LocalDate.of(2025, 11, 1), 1, 0)
        ));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(1L, inicio, fim)))
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

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, inicio, fim)))
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

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, alunoDeOutroPersonal, filtro(null, inicio, fim)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Aluno não encontrado para este Personal Trainer.");
    }

    @Test
    @DisplayName("US16 RN-014: Aluno inativo não emite o próprio relatório")
    void US16_RN014_alunoInativo_bloqueadoParaAluno() {
        aluno.setStatus(StatusConvite.INATIVO);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloAluno(usuarioAluno, filtro(null, inicio, fim)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro. Este aluno está inativo.");
    }

    @Test
    @DisplayName("US16 RN-014: Histórico do aluno inativo é preservado e o personal ainda emite o relatório")
    void US16_RN014_alunoInativo_personalEmiteRelatorioDoHistorico() throws IOException {
        aluno.setStatus(StatusConvite.INATIVO);
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, inicio, fim));

        assertThat(extrairTexto(relatorio.conteudo())).contains("Yasmin Rezende");
    }

    @Test
    @DisplayName("US16 RN-017: Métrica descontinuada aparece no relatório identificada como descontinuada")
    void US16_RN017_metricaDescontinuada_apareceMarcadaNoRelatorio() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, inicio, fim));

        assertThat(extrairTexto(relatorio.conteudo())).contains("Braçadas Antigas").contains("métrica descontinuada");
    }

    @Test
    @DisplayName("US16 RN-018 / US20 C2: RPE pulado aparece como não informado no relatório")
    void US16_RN018_rpeNaoInformado_apareceComoNaoInformado() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, inicio, fim));

        assertThat(extrairTexto(relatorio.conteudo())).contains("não informado").contains("Nível de esforço (RPE)").contains("6,5");
    }

    @Test
    @DisplayName("US16: Período não informado impede a geração do relatório")
    void US16_periodoNaoInformado_lancaBusinessException() {
        mockPersonalComAluno();

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, null, fim)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe a data de início e a data de fim do período do relatório.");
    }

    @Test
    @DisplayName("US16: Data de início posterior à data de fim impede a geração do relatório")
    void US16_periodoInvalido_dataInicioPosteriorAFim_lancaBusinessException() {
        mockPersonalComAluno();

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, fim, inicio)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A data de início não pode ser posterior à data de fim.");
    }

    @Test
    @DisplayName("US16: Modalidade que o aluno não pratica e sem histórico retorna não encontrada")
    void US16_modalidadeInexistenteParaAluno_lancaNotFound() {
        mockPersonalComAluno();
        when(desempenhoRepository.findIntervalosRegistrosByAlunoId(aluno.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(99L, inicio, fim)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Modalidade não encontrada para este aluno.");
    }

    @Test
    @DisplayName("US16 Personalização: Relatório traz apenas as métricas escolhidas")
    void US16_metricasEscolhidas_relatorioSoTrazAsSelecionadas() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findDiasRegistrosMetricasByAlunoId(aluno.getId()))
                .thenReturn(List.of(diaMetrica(modalidade, metricaCarga, LocalDate.of(2026, 3, 2)), diaMetrica(modalidade, metricaTempo, LocalDate.of(2026, 3, 2))));
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtroMetricas(metricaCarga));

        assertThat(extrairTexto(relatorio.conteudo()))
                .contains("Distância Percorrida")
                .contains("Sessões registradas")
                .doesNotContain("Tempo 100m")
                .doesNotContain("Braçadas Antigas");
    }

    @Test
    @DisplayName("US16 Personalização: Modalidade sem nenhuma métrica escolhida fica fora do relatório")
    void US16_metricasEscolhidas_excluiModalidadeSemMetricaSelecionada() throws IOException {
        Modalidade tenis = Modalidade.builder().id(2L).nome("Tênis").build();
        Metrica metricaTenis = Metrica.builder().id(UUID.randomUUID()).titulo("Saques").tipo(TipoMetrica.NUMERICO).ativo(true).build();
        rotinaTreino.setModalidade(tenis);
        Desempenho desempenhoTenis = criarDesempenho(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 3), 5, List.of(registro(metricaTenis, "40")));
        rotinaTreino = RotinaTreino.builder().id(UUID.randomUUID()).titulo("Natação").modalidade(modalidade).build();
        Desempenho desempenhoNatacao = criarDesempenho(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 4), 5, List.of(registro(metricaCarga, "1500")));

        mockPersonalComAluno();
        when(desempenhoRepository.findDiasRegistrosMetricasByAlunoId(aluno.getId())).thenReturn(List.of(diaMetrica(modalidade, metricaCarga, LocalDate.of(2026, 3, 4))));
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(List.of(desempenhoTenis, desempenhoNatacao));

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtroMetricas(metricaCarga));

        assertThat(extrairTexto(relatorio.conteudo())).contains("Natação").doesNotContain("Tênis").doesNotContain("Saques");
    }

    @Test
    @DisplayName("US16 Personalização: Métrica sem registros do aluno é recusada (RN-005/006)")
    void US16_metricaSemRegistrosDoAluno_lancaNotFound() {
        mockPersonalComAluno();
        when(desempenhoRepository.findDiasRegistrosMetricasByAlunoId(aluno.getId())).thenReturn(List.of(diaMetrica(modalidade, metricaCarga, LocalDate.of(2026, 3, 2))));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtroMetricas(metricaTempo)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Métrica não encontrada para este aluno.");
        verify(relatorioPdfGenerator, never()).gerar(any());
    }

    @Test
    @DisplayName("US16 Personalização: Métrica escolhida sem registros no período sugere o intervalo da própria métrica")
    void US16_metricaEscolhidaForaDoPeriodo_sugereIntervaloDaMetrica() {
        List<DiaRegistrosMetricaDTO> diasMetricas = List.of(
                diaMetrica(modalidade, metricaCarga, LocalDate.of(2026, 3, 2)),
                diaMetrica(modalidade, metricaTempo, LocalDate.of(2026, 1, 12)),
                diaMetrica(modalidade, metricaTempo, LocalDate.of(2026, 1, 19))
        );
        mockPersonalComAluno();
        when(desempenhoRepository.findDiasRegistrosMetricasByAlunoId(aluno.getId())).thenReturn(diasMetricas);
        when(desempenhoRepository.findDiasRegistrosByAlunoId(aluno.getId())).thenReturn(List.of(
                dia(modalidade, LocalDate.of(2026, 1, 12), 1, 0),
                dia(modalidade, LocalDate.of(2026, 1, 19), 1, 0),
                dia(modalidade, LocalDate.of(2026, 3, 2), 1, 0)
        ));
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(List.of(
                criarDesempenho(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), 6, List.of(registro(metricaCarga, "1500")))
        ));

        FiltroRelatorioDTO filtro = new FiltroRelatorioDTO(null, inicio, fim, null, Set.of(metricaTempo.getId()), Set.of(ConteudoRelatorio.VALORES));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro))
                .isInstanceOf(RelatorioSemDadosException.class)
                .satisfies(ex -> {
                    RelatorioSemDadosException semDados = (RelatorioSemDadosException) ex;
                    assertThat(semDados.getDataInicioSugerida()).isEqualTo(LocalDate.of(2026, 1, 12));
                    assertThat(semDados.getDataFimSugerida()).isEqualTo(LocalDate.of(2026, 1, 19));
                });
    }

    @Test
    @DisplayName("US16 Personalização: Relatório traz apenas os blocos escolhidos")
    void US16_conteudosEscolhidos_relatorioSoTrazOsBlocosSelecionados() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtroConteudos(ConteudoRelatorio.VALORES));

        assertThat(extrairTexto(relatorio.conteudo()))
                .contains("Valores registrados")
                .contains("2.000")
                .doesNotContain("Resumo do período")
                .doesNotContain("Evolução")
                .doesNotContain("Data planejada")
                .doesNotContain("Nível de esforço (RPE)");
    }

    @Test
    @DisplayName("US16 Personalização: Sem o bloco RPE, o gráfico e a coluna de RPE ficam fora do relatório")
    void US16_semConteudoRpe_removeGraficoEColunaDeRpe() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(),
                filtroConteudos(ConteudoRelatorio.GRAFICOS, ConteudoRelatorio.SESSOES));

        assertThat(extrairTexto(relatorio.conteudo()))
                .contains("Evolução")
                .contains("Tempo 100m")
                .contains("Data planejada")
                .doesNotContain("Nível de esforço (RPE)")
                .doesNotContain("não informado");
    }

    @Test
    @DisplayName("US16 Personalização: Só o bloco RPE, sem RPE no período, sugere as datas com RPE informado")
    void US16_soRpeSemRegistrosNoPeriodo_sugereIntervaloComRpe() {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(List.of(
                criarDesempenho(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), null, List.of(registro(metricaCarga, "1500")))
        ));
        when(desempenhoRepository.findDiasRegistrosByAlunoId(aluno.getId())).thenReturn(List.of(
                dia(modalidade, LocalDate.of(2026, 3, 2), 1, 0),
                dia(modalidade, LocalDate.of(2026, 2, 9), 1, 1),
                dia(modalidade, LocalDate.of(2026, 1, 5), 1, 0)
        ));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtroConteudos(ConteudoRelatorio.RPE)))
                .isInstanceOf(RelatorioSemDadosException.class)
                .satisfies(ex -> {
                    RelatorioSemDadosException semDados = (RelatorioSemDadosException) ex;
                    assertThat(semDados.getDataInicioSugerida()).isEqualTo(LocalDate.of(2026, 2, 9));
                    assertThat(semDados.getDataFimSugerida()).isEqualTo(LocalDate.of(2026, 2, 9));
                });
    }

    @Test
    @DisplayName("US16 Personalização: Só gráficos com métricas de texto não gera relatório vazio")
    void US16_soGraficosComMetricasDeTexto_lancaSemDados() {
        Metrica metricaTexto = Metrica.builder().id(UUID.randomUUID()).titulo("Sensação").tipo(TipoMetrica.TEXTO).ativo(true).build();
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(List.of(
                criarDesempenho(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), 6, List.of(registro(metricaTexto, "Boa")))
        ));

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtroConteudos(ConteudoRelatorio.GRAFICOS)))
                .isInstanceOf(RelatorioSemDadosException.class)
                .hasMessage("Não há dados para o período selecionado");
    }

    @Test
    @DisplayName("US16 Personalização: Relatório traz apenas as datas escolhidas e informa a quantidade")
    void US16_datasEscolhidas_relatorioSoTrazAsSelecionadas() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        FiltroRelatorioDTO filtro = new FiltroRelatorioDTO(null, inicio, fim,
                Set.of(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 16)), null, null);
        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro);

        assertThat(extrairTexto(relatorio.conteudo()))
                .contains("2 selecionadas")
                .contains("01/03/2026 – 31/03/2026")
                .contains("02/03/2026")
                .contains("16/03/2026")
                .doesNotContain("10/03/2026")
                .doesNotContain("09/03/2026")
                .doesNotContain("1.800");
    }

    @Test
    @DisplayName("US16 Personalização: Sem datas escolhidas, o relatório não menciona a seleção")
    void US16_semDatasEscolhidas_naoInformaSelecao() throws IOException {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, inicio, fim));

        assertThat(extrairTexto(relatorio.conteudo())).doesNotContain("selecionada").contains("10/03/2026");
    }

    @Test
    @DisplayName("US16 Personalização: Datas escolhidas sem registros no período retornam sem dados")
    void US16_datasEscolhidasSemRegistros_lancaSemDados() {
        mockPersonalComAluno();
        when(desempenhoRepository.findByAlunoIdAndPeriodoWithMetricas(aluno.getId(), inicio, fim)).thenReturn(historicoPadrao());

        FiltroRelatorioDTO filtro = new FiltroRelatorioDTO(null, inicio, fim, Set.of(LocalDate.of(2026, 3, 3)), null, null);

        assertThatThrownBy(() -> relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro))
                .isInstanceOf(RelatorioSemDadosException.class)
                .hasMessage("Não há dados para o período selecionado");
    }

    @Test
    @DisplayName("US16 Disponibilidade: Lista datas, datas com RPE e métricas por modalidade")
    void US16_disponibilidade_listaDatasEMetricasPorModalidade() {
        Modalidade tenis = Modalidade.builder().id(2L).nome("Tênis").build();
        LocalDate dia1 = LocalDate.of(2026, 3, 2);
        LocalDate dia2 = LocalDate.of(2026, 3, 10);
        mockPersonalComAluno();
        when(desempenhoRepository.findDiasRegistrosByAlunoId(aluno.getId())).thenReturn(List.of(
                dia(modalidade, dia2, 2, 0),
                dia(modalidade, dia1, 1, 1),
                dia(tenis, LocalDate.of(2025, 12, 1), 1, 0)
        ));
        when(desempenhoRepository.findDiasRegistrosMetricasByAlunoId(aluno.getId())).thenReturn(List.of(
                diaMetrica(modalidade, metricaTempo, dia2),
                diaMetrica(modalidade, metricaCarga, dia2),
                diaMetrica(modalidade, metricaCarga, dia1),
                diaMetrica(modalidade, metricaDescontinuada, dia1)
        ));

        DisponibilidadeRelatorioDTO disponibilidade = relatorioPerformanceService.consultarDisponibilidadePeloPersonal(usuarioPersonal, aluno.getId());

        assertThat(disponibilidade.dataInicio()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(disponibilidade.dataFim()).isEqualTo(dia2);
        assertThat(disponibilidade.totalSessoes()).isEqualTo(4);
        assertThat(disponibilidade.modalidades()).extracting(ModalidadeDisponivelDTO::modalidade).containsExactly("Natação", "Tênis");

        ModalidadeDisponivelDTO natacao = disponibilidade.modalidades().get(0);
        assertThat(natacao.datas()).containsExactly(dia1, dia2);
        assertThat(natacao.datasRpe()).containsExactly(dia1);
        assertThat(natacao.totalSessoes()).isEqualTo(3);
        assertThat(natacao.metricas()).extracting(m -> m.titulo())
                .containsExactly("Braçadas Antigas", "Distância Percorrida", "Tempo 100m");
        assertThat(natacao.metricas().get(1).datas()).containsExactly(dia1, dia2);
        assertThat(natacao.metricas().get(1).totalRegistros()).isEqualTo(2);
        assertThat(natacao.metricas().get(0).descontinuada()).isTrue();
        assertThat(natacao.metricas().get(0).statusMetrica()).isEqualTo("métrica descontinuada");

        assertThat(disponibilidade.modalidades().get(1).metricas()).isEmpty();
    }

    @Test
    @DisplayName("US16 Disponibilidade: Aluno sem registros recebe lista vazia e intervalo nulo")
    void US16_disponibilidade_semRegistros_retornaVazio() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        DisponibilidadeRelatorioDTO disponibilidade = relatorioPerformanceService.consultarDisponibilidadePeloAluno(usuarioAluno);

        assertThat(disponibilidade.modalidades()).isEmpty();
        assertThat(disponibilidade.dataInicio()).isNull();
        assertThat(disponibilidade.dataFim()).isNull();
        assertThat(disponibilidade.totalSessoes()).isZero();
    }

    @Test
    @DisplayName("US16 RN-006: Personal não consulta a disponibilidade de aluno de outro personal")
    void US16_RN006_disponibilidade_personalDeOutroAluno_lancaNotFound() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        UUID alunoDeOutroPersonal = UUID.randomUUID();
        when(alunoRepository.findByIdAndPersonalId(alunoDeOutroPersonal, personalTrainer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> relatorioPerformanceService.consultarDisponibilidadePeloPersonal(usuarioPersonal, alunoDeOutroPersonal))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Aluno não encontrado para este Personal Trainer.");
    }

    @Test
    @DisplayName("US16 RN-014: Aluno inativo não consulta a própria disponibilidade, mas o personal consulta")
    void US16_RN014_disponibilidade_alunoInativo() {
        aluno.setStatus(StatusConvite.INATIVO);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        mockPersonalComAluno();
        when(desempenhoRepository.findDiasRegistrosByAlunoId(aluno.getId())).thenReturn(List.of(dia(modalidade, LocalDate.of(2026, 3, 2), 1, 1)));

        assertThatThrownBy(() -> relatorioPerformanceService.consultarDisponibilidadePeloAluno(usuarioAluno))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro. Este aluno está inativo.");
        assertThat(relatorioPerformanceService.consultarDisponibilidadePeloPersonal(usuarioPersonal, aluno.getId()).modalidades()).hasSize(1);
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
        RelatorioPdfDTO relatorio = relatorioPerformanceService.gerarRelatorioPeloPersonal(usuarioPersonal, aluno.getId(), filtro(null, inicioAmplo, fimAmplo));
        Duration tempo = Duration.ofNanos(System.nanoTime() - inicioExecucao);

        assertThat(relatorio.conteudo()).isNotEmpty();
        assertThat(tempo).isLessThan(Duration.ofSeconds(40));
    }
}
