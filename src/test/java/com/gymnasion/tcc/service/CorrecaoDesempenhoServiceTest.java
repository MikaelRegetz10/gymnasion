package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.*;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.MetricasInvalidasException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AuditoriaDesempenhoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import com.gymnasion.tcc.repository.SessaoTreinoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrecaoDesempenhoServiceTest {

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;

    @Mock
    private SessaoTreinoRepository sessaoTreinoRepository;

    @Mock
    private AuditoriaDesempenhoRepository auditoriaDesempenhoRepository;

    @Mock
    private AcompanhamentoTreinoService acompanhamentoTreinoService;

    @InjectMocks
    private CorrecaoDesempenhoService correcaoDesempenhoService;

    @Captor
    private ArgumentCaptor<List<AuditoriaDesempenho>> auditoriasCaptor;

    private Usuario usuarioPersonal;
    private PersonalTrainer personalTrainer;
    private Aluno aluno;
    private Metrica tempo;
    private Metrica bracadas;
    private Metrica analiseTecnica;
    private Metrica percepcaoAluno;
    private Metrica descontinuada;
    private SessaoTreino sessao;
    private Desempenho desempenho;
    private UUID sessaoId;

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

        aluno = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(Usuario.builder().id(UUID.randomUUID()).nome("Aluno Lucas").role(Role.ALUNO).build())
                .personal(personalTrainer)
                .build();

        Modalidade modalidade = Modalidade.builder().id(1L).nome("Natação").build();

        tempo = metrica("Tempo 50m", TipoMetrica.TEMPO, true, ResponsavelPreenchimento.ALUNO, true, modalidade);
        bracadas = metrica("Braçadas", TipoMetrica.NUMERICO, false, ResponsavelPreenchimento.AMBOS, true, modalidade);
        analiseTecnica = metrica("Análise Técnica", TipoMetrica.TEXTO, true, ResponsavelPreenchimento.PERSONAL, true, modalidade);
        percepcaoAluno = metrica("Percepção", TipoMetrica.TEXTO, false, ResponsavelPreenchimento.ALUNO, true, modalidade);
        descontinuada = metrica("Métrica Antiga", TipoMetrica.NUMERICO, false, ResponsavelPreenchimento.AMBOS, false, modalidade);

        RotinaTreino rotina = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Rotina Pré-Competição")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .frequenciaSemanal(3)
                .alunos(new HashSet<>(Set.of(aluno)))
                .metricas(new HashSet<>(Set.of(tempo, bracadas, analiseTecnica, percepcaoAluno, descontinuada)))
                .build();

        sessaoId = UUID.randomUUID();
        sessao = SessaoTreino.builder()
                .id(sessaoId)
                .rotinaTreino(rotina)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .dataReal(LocalDate.of(2026, 10, 22))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .build();

        desempenho = Desempenho.builder()
                .id(UUID.randomUUID())
                .sessaoTreino(sessao)
                .aluno(aluno)
                .registradoPor("ALUNO")
                .dataRegistro(OffsetDateTime.now())
                .nivelCansaco(7)
                .build();
        desempenho.getRegistrosMetricas().add(registro(tempo, "00:29"));
        desempenho.getRegistrosMetricas().add(registro(bracadas, "30"));
        sessao.setDesempenho(desempenho);
    }

    private Metrica metrica(String titulo, TipoMetrica tipo, boolean obrigatoria, ResponsavelPreenchimento quemPreenche,
                            boolean ativo, Modalidade modalidade) {
        return Metrica.builder()
                .id(UUID.randomUUID())
                .titulo(titulo)
                .tipo(tipo)
                .obrigatoria(obrigatoria)
                .quemPreenche(quemPreenche)
                .ativo(ativo)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .build();
    }

    private RegistroMetrica registro(Metrica metrica, String valor) {
        return RegistroMetrica.builder()
                .id(UUID.randomUUID())
                .desempenho(desempenho)
                .metrica(metrica)
                .valor(valor)
                .build();
    }

    private String valorRegistrado(Metrica metrica) {
        return desempenho.getRegistrosMetricas().stream()
                .filter(rm -> rm.getMetrica().getId().equals(metrica.getId()))
                .map(RegistroMetrica::getValor)
                .findFirst()
                .orElse(null);
    }

    private void personalResponsavelAcessaSessao() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId())).thenReturn(Optional.of(sessao));
    }

    private void salvamentoRetornaSessao() {
        when(sessaoTreinoRepository.save(any(SessaoTreino.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("US23 Cenário 1: Personal acessa o registro com opção de corrigir valores e preencher pendentes")
    void US23_cenario1_acessarRegistroComOpcaoDeCorrigir() {
        personalResponsavelAcessaSessao();

        DesempenhoDetalheResponseDTO detalhe = correcaoDesempenhoService.consultarRegistro(usuarioPersonal, sessaoId);

        assertThat(detalhe.desempenhoId()).isEqualTo(desempenho.getId());
        assertThat(detalhe.dataPlanejada()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(detalhe.dataReal()).isEqualTo(LocalDate.of(2026, 10, 22));
        // Métrica descontinuada sem valor não aparece (RN-017)
        assertThat(detalhe.metricas())
                .extracting(DesempenhoDetalheResponseDTO.MetricaDesempenhoDTO::titulo)
                .containsExactly("Análise Técnica", "Braçadas", "Percepção", "Tempo 50m");

        Map<String, DesempenhoDetalheResponseDTO.MetricaDesempenhoDTO> porTitulo = new HashMap<>();
        detalhe.metricas().forEach(m -> porTitulo.put(m.titulo(), m));
        assertThat(porTitulo.get("Tempo 50m").correcaoPermitida()).isTrue();
        assertThat(porTitulo.get("Tempo 50m").valor()).isEqualTo("00:29");
        assertThat(porTitulo.get("Análise Técnica").inclusaoPermitida()).isTrue();
        assertThat(porTitulo.get("Análise Técnica").valor()).isNull();
        assertThat(porTitulo.get("Percepção").inclusaoPermitida()).isFalse();
    }

    @Test
    @DisplayName("US23 Cenários 2 e 5: Corrigir valor válido atualiza o registro e audita como correção")
    void US23_cenario2_e_5_corrigirValorValidoGeraAuditoriaDeCorrecao() {
        personalResponsavelAcessaSessao();
        salvamentoRetornaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(
                null, null, List.of(new RegistrarResultadoMetricaDTO(tempo.getId(), "00:31"))
        );

        DesempenhoDetalheResponseDTO detalhe = correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto);

        assertThat(valorRegistrado(tempo)).isEqualTo("00:31");
        assertThat(detalhe.metricas()).anyMatch(m -> m.titulo().equals("Tempo 50m") && m.valor().equals("00:31"));

        verify(auditoriaDesempenhoRepository).saveAll(auditoriasCaptor.capture());
        assertThat(auditoriasCaptor.getValue()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getTipoOperacao()).isEqualTo(TipoOperacaoAuditoria.CORRECAO);
            assertThat(auditoria.getCampo()).isEqualTo(CampoAuditado.METRICA);
            assertThat(auditoria.getMetrica()).isSameAs(tempo);
            assertThat(auditoria.getValorAnterior()).isEqualTo("00:29");
            assertThat(auditoria.getValorNovo()).isEqualTo("00:31");
            assertThat(auditoria.getUsuario()).isSameAs(usuarioPersonal);
            assertThat(auditoria.getDesempenho()).isSameAs(desempenho);
        });
        verify(acompanhamentoTreinoService).sincronizarNotificacaoParaAluno(aluno);
    }

    @Test
    @DisplayName("US23 Cenário 2: Personal pode corrigir valor registrado de métrica exclusiva do aluno")
    void US23_cenario2_corrigirValorDeMetricaDoAluno() {
        personalResponsavelAcessaSessao();
        salvamentoRetornaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(
                null, null, List.of(new RegistrarResultadoMetricaDTO(tempo.getId(), "00:30"))
        );

        correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto);

        assertThat(tempo.getQuemPreenche()).isEqualTo(ResponsavelPreenchimento.ALUNO);
        assertThat(valorRegistrado(tempo)).isEqualTo("00:30");
    }

    @Test
    @DisplayName("US23 Cenário 3: Corrigir a data real preserva a data planejada e é auditado")
    void US23_cenario3_corrigirDataRealPreservaPlanejada() {
        personalResponsavelAcessaSessao();
        salvamentoRetornaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(LocalDate.of(2026, 10, 23), null, null);

        DesempenhoDetalheResponseDTO detalhe = correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto);

        assertThat(detalhe.dataReal()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(detalhe.dataPlanejada()).isEqualTo(LocalDate.of(2026, 10, 20));

        verify(auditoriaDesempenhoRepository).saveAll(auditoriasCaptor.capture());
        assertThat(auditoriasCaptor.getValue()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getTipoOperacao()).isEqualTo(TipoOperacaoAuditoria.CORRECAO);
            assertThat(auditoria.getCampo()).isEqualTo(CampoAuditado.DATA_REAL);
            assertThat(auditoria.getValorAnterior()).isEqualTo("2026-10-22");
            assertThat(auditoria.getValorNovo()).isEqualTo("2026-10-23");
        });
        verify(acompanhamentoTreinoService, never()).sincronizarNotificacaoParaAluno(any());
    }

    @Test
    @DisplayName("US23 Cenário 4: Corrigir observações existentes é auditado como correção")
    void US23_cenario4_corrigirObservacoes() {
        desempenho.setObservacoes("Aluno relatou cansaço.");
        personalResponsavelAcessaSessao();
        salvamentoRetornaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(null, "  Aluno relatou dor no ombro.  ", null);

        DesempenhoDetalheResponseDTO detalhe = correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto);

        assertThat(detalhe.observacoes()).isEqualTo("Aluno relatou dor no ombro.");
        verify(auditoriaDesempenhoRepository).saveAll(auditoriasCaptor.capture());
        assertThat(auditoriasCaptor.getValue()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getTipoOperacao()).isEqualTo(TipoOperacaoAuditoria.CORRECAO);
            assertThat(auditoria.getCampo()).isEqualTo(CampoAuditado.OBSERVACOES);
            assertThat(auditoria.getValorAnterior()).isEqualTo("Aluno relatou cansaço.");
            assertThat(auditoria.getValorNovo()).isEqualTo("Aluno relatou dor no ombro.");
        });
    }

    @Test
    @DisplayName("US23 Cenário 4: Primeiras observações do desempenho são auditadas como inclusão")
    void US23_cenario4_primeirasObservacoesSaoInclusao() {
        personalResponsavelAcessaSessao();
        salvamentoRetornaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(null, "Boa evolução na virada.", null);

        correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto);

        verify(auditoriaDesempenhoRepository).saveAll(auditoriasCaptor.capture());
        assertThat(auditoriasCaptor.getValue()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getTipoOperacao()).isEqualTo(TipoOperacaoAuditoria.INCLUSAO);
            assertThat(auditoria.getValorAnterior()).isNull();
        });
    }

    @Test
    @DisplayName("US23 Cenário 6: Valores inválidos impedem o salvamento, identificam os campos e preservam os dados")
    void US23_cenario6_valoresInvalidosNaoAlteramNada() {
        personalResponsavelAcessaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(
                LocalDate.of(2026, 10, 25),
                "   ",
                List.of(
                        new RegistrarResultadoMetricaDTO(tempo.getId(), "1:75"),
                        new RegistrarResultadoMetricaDTO(bracadas.getId(), "35")
                )
        );

        assertThatThrownBy(() -> correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(MetricasInvalidasException.class)
                .satisfies(ex -> assertThat(((MetricasInvalidasException) ex).getCamposInvalidos())
                        .containsOnlyKeys("observacoes", tempo.getId().toString()));

        // Dados anteriores preservados, inclusive os campos válidos da mesma requisição
        assertThat(valorRegistrado(tempo)).isEqualTo("00:29");
        assertThat(valorRegistrado(bracadas)).isEqualTo("30");
        assertThat(sessao.getDataReal()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(desempenho.getObservacoes()).isNull();
        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
        verify(auditoriaDesempenhoRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("US23 Cenário 7: Preencher métrica pendente do personal é auditado como inclusão e atualiza pendências")
    void US23_cenario7_preencherPendenteGeraInclusao() {
        personalResponsavelAcessaSessao();
        salvamentoRetornaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(
                null, null, List.of(new RegistrarResultadoMetricaDTO(analiseTecnica.getId(), "Boa técnica de virada"))
        );

        DesempenhoDetalheResponseDTO detalhe = correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto);

        assertThat(valorRegistrado(analiseTecnica)).isEqualTo("Boa técnica de virada");
        assertThat(detalhe.metricas()).anyMatch(m -> m.titulo().equals("Análise Técnica") && !m.inclusaoPermitida() && m.correcaoPermitida());

        verify(auditoriaDesempenhoRepository).saveAll(auditoriasCaptor.capture());
        assertThat(auditoriasCaptor.getValue()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getTipoOperacao()).isEqualTo(TipoOperacaoAuditoria.INCLUSAO);
            assertThat(auditoria.getCampo()).isEqualTo(CampoAuditado.METRICA);
            assertThat(auditoria.getValorAnterior()).isNull();
            assertThat(auditoria.getValorNovo()).isEqualTo("Boa técnica de virada");
            assertThat(auditoria.getUsuario()).isSameAs(usuarioPersonal);
        });
        verify(acompanhamentoTreinoService).sincronizarNotificacaoParaAluno(aluno);
    }

    @Test
    @DisplayName("US23 Cenário 7: Personal não inclui valor em métrica exclusiva do aluno não preenchida")
    void US23_cenario7_naoIncluiMetricaExclusivaDoAluno() {
        personalResponsavelAcessaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(
                null, null, List.of(new RegistrarResultadoMetricaDTO(percepcaoAluno.getId(), "Cansado"))
        );

        assertThatThrownBy(() -> correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A métrica 'Percepção' é de preenchimento exclusivo do aluno.");
        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US23 / RN-017: Métrica descontinuada não recebe inclusão")
    void US23_rn017_naoIncluiMetricaDescontinuada() {
        personalResponsavelAcessaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(
                null, null, List.of(new RegistrarResultadoMetricaDTO(descontinuada.getId(), "10"))
        );

        assertThatThrownBy(() -> correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A métrica 'Métrica Antiga' foi descontinuada e não aceita novos registros.");
    }

    @Test
    @DisplayName("US23 Cenário 8: Personal que não é responsável pela sessão não acessa a correção")
    void US23_cenario8_personalNaoResponsavelNaoCorrige() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personalTrainer.getId())).thenReturn(Optional.empty());

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(LocalDate.of(2026, 10, 23), null, null);

        assertThatThrownBy(() -> correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(NotFoundException.class);
        verify(sessaoTreinoRepository, never()).save(any(SessaoTreino.class));
    }

    @Test
    @DisplayName("US23 Cenário 8: Registro de aluno de outro personal não pode ser corrigido")
    void US23_cenario8_alunoDeOutroPersonalNaoCorrige() {
        aluno.setPersonal(PersonalTrainer.builder().id(UUID.randomUUID()).build());
        personalResponsavelAcessaSessao();

        assertThatThrownBy(() -> correcaoDesempenhoService.consultarRegistro(usuarioPersonal, sessaoId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("US23: Sessão sem registro de desempenho não pode ser corrigida")
    void US23_sessaoSemDesempenhoNaoPodeSerCorrigida() {
        sessao.setDesempenho(null);
        personalResponsavelAcessaSessao();

        assertThatThrownBy(() -> correcaoDesempenhoService.consultarRegistro(usuarioPersonal, sessaoId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esta sessão ainda não possui registro de desempenho.");
    }

    @Test
    @DisplayName("US23: Valores iguais aos registrados não geram alteração nem auditoria")
    void US23_semAlteracaoNaoGeraAuditoria() {
        personalResponsavelAcessaSessao();

        CorrigirDesempenhoRequestDTO dto = new CorrigirDesempenhoRequestDTO(
                LocalDate.of(2026, 10, 22), null, List.of(new RegistrarResultadoMetricaDTO(tempo.getId(), "00:29"))
        );

        assertThatThrownBy(() -> correcaoDesempenhoService.corrigirRegistro(usuarioPersonal, sessaoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Nenhuma alteração informada.");
        verify(auditoriaDesempenhoRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("US23 Cenário 5: Histórico de alterações retorna valores anteriores, novos, responsável e data/hora")
    void US23_cenario5_consultarHistoricoDeAlteracoes() {
        personalResponsavelAcessaSessao();
        OffsetDateTime quando = OffsetDateTime.now();
        AuditoriaDesempenho auditoria = AuditoriaDesempenho.builder()
                .id(UUID.randomUUID())
                .desempenho(desempenho)
                .tipoOperacao(TipoOperacaoAuditoria.CORRECAO)
                .campo(CampoAuditado.METRICA)
                .metrica(tempo)
                .valorAnterior("00:29")
                .valorNovo("00:31")
                .usuario(usuarioPersonal)
                .dataHora(quando)
                .build();
        when(auditoriaDesempenhoRepository.findByDesempenhoIdOrderByDataHoraAsc(desempenho.getId())).thenReturn(List.of(auditoria));

        List<AuditoriaDesempenhoResponseDTO> historico = correcaoDesempenhoService.consultarAuditoria(usuarioPersonal, sessaoId);

        assertThat(historico).singleElement().satisfies(item -> {
            assertThat(item.tituloMetrica()).isEqualTo("Tempo 50m");
            assertThat(item.valorAnterior()).isEqualTo("00:29");
            assertThat(item.valorNovo()).isEqualTo("00:31");
            assertThat(item.nomeUsuario()).isEqualTo("Treinador Carlos");
            assertThat(item.dataHora()).isEqualTo(quando);
        });
    }
}
