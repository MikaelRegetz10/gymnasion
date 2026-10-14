package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import com.gymnasion.tcc.domain.enums.TipoNotificacao;
import com.gymnasion.tcc.dto.AcompanhamentoAlunoResponseDTO;
import com.gymnasion.tcc.dto.NotificacaoResponseDTO;
import com.gymnasion.tcc.dto.ResumoPendenciasResponseDTO;
import com.gymnasion.tcc.dto.SessaoAcompanhamentoDTO;
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
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcompanhamentoTreinoServiceTest {

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private RotinaTreinoRepository rotinaTreinoRepository;

    @Mock
    private SessaoTreinoRepository sessaoTreinoRepository;

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @InjectMocks
    private AcompanhamentoTreinoService acompanhamentoTreinoService;

    private Usuario usuarioPersonal;
    private PersonalTrainer personalTrainer;
    private Usuario usuarioAluno;
    private Aluno aluno;
    private Modalidade modalidade;
    private Metrica metricaPersonal;
    private Metrica metricaAluno;
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

        metricaPersonal = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Avaliação Técnica do Nado")
                .tipo(TipoMetrica.TEXTO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .quemPreenche(ResponsavelPreenchimento.PERSONAL)
                .obrigatoria(true)
                .build();

        metricaAluno = Metrica.builder()
                .id(UUID.randomUUID())
                .titulo("Tempo 100m Livre")
                .tipo(TipoMetrica.TEMPO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .quemPreenche(ResponsavelPreenchimento.ALUNO)
                .obrigatoria(true)
                .build();

        rotinaTreino = RotinaTreino.builder()
                .id(UUID.randomUUID())
                .titulo("Rotina Velocidade")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .frequenciaSemanal(3)
                .ativo(true)
                .alunos(new HashSet<>(Set.of(aluno)))
                .metricas(new HashSet<>(Set.of(metricaPersonal, metricaAluno)))
                .build();
    }

    @Test
    @DisplayName("US24 Cenários 1, 2 e 3: Obter visão geral do aluno com sessões planejadas, concluídas e contagem de pendências")
    void US24_cenarios1_2_3_visaoGeralDoAlunoComPendencias() {
        // Dado: 1 sessão planejada e 1 sessão concluída pelo aluno com pendência do personal
        SessaoTreino sessao1 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .dataReal(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .validadaPersonal(false)
                .build();

        // Aluno registrou a métrica dele, mas a métrica do personal ficou pendente
        Desempenho desempenhoSessao1 = Desempenho.builder()
                .id(UUID.randomUUID())
                .sessaoTreino(sessao1)
                .aluno(aluno)
                .registradoPor("ALUNO")
                .dataRegistro(OffsetDateTime.now())
                .registrosMetricas(new ArrayList<>(List.of(
                        RegistroMetrica.builder()
                                .id(UUID.randomUUID())
                                .metrica(metricaAluno)
                                .valor("00:58")
                                .build()
                )))
                .build();
        sessao1.setDesempenho(desempenhoSessao1);

        SessaoTreino sessao2 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(2)
                .dataPlanejada(LocalDate.of(2026, 10, 27))
                .status(StatusSessaoTreino.PLANEJADA)
                .concluidaAluno(false)
                .validadaPersonal(false)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personalTrainer.getId(), aluno.getId()))
                .thenReturn(List.of(rotinaTreino));
        when(sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaTreino.getId()))
                .thenReturn(List.of(sessao1, sessao2));
        when(notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(usuarioPersonal.getId(), TipoNotificacao.METRICAS_PENDENTES, aluno.getId()))
                .thenReturn(Optional.empty());

        // Quando: consulta acompanhamento do aluno
        AcompanhamentoAlunoResponseDTO response = acompanhamentoTreinoService.obterAcompanhamentoAluno(usuarioPersonal, aluno.getId());

        // Então: retorna visão geral completa
        assertThat(response).isNotNull();
        assertThat(response.alunoId()).isEqualTo(aluno.getId());
        assertThat(response.rotinaAtivaId()).isEqualTo(rotinaTreino.getId());
        assertThat(response.totalSessoesConcluidas()).isEqualTo(1);
        assertThat(response.totalSessoesPlanejadas()).isEqualTo(1);

        // US-24 C3: Contagem de pendências (metricaPersonal pendente nas duas sessões = 2)
        assertThat(response.quantidadeMetricasPendentes()).isEqualTo(2);
        assertThat(response.todasMetricasPendentes()).hasSize(2);
        assertThat(response.todasMetricasPendentes().get(0).tituloMetrica()).isEqualTo("Avaliação Técnica do Nado");

        // US-24 C4: Notificação criada automaticamente para o personal
        verify(notificacaoRepository).save(any(Notificacao.class));
    }

    @Test
    @DisplayName("US24 Cenário 10: Sessão concluída pelo aluno com pendência do personal continua CONCLUIDA e indica pendência separadamente")
    void US24_cenario10_sessaoConcluidaComPendenciasContinuaConcluidaEIndicaSeparadamente() {
        SessaoTreino sessao = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .dataReal(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.CONCLUIDA)
                .concluidaAluno(true)
                .validadaPersonal(false)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personalTrainer.getId(), aluno.getId()))
                .thenReturn(List.of(rotinaTreino));
        when(sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaTreino.getId()))
                .thenReturn(List.of(sessao));
        when(notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(usuarioPersonal.getId(), TipoNotificacao.METRICAS_PENDENTES, aluno.getId()))
                .thenReturn(Optional.empty());

        AcompanhamentoAlunoResponseDTO response = acompanhamentoTreinoService.obterAcompanhamentoAluno(usuarioPersonal, aluno.getId());

        SessaoAcompanhamentoDTO sessaoDTO = response.sessoes().get(0);
        // Permanece concluída
        assertThat(sessaoDTO.status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
        assertThat(sessaoDTO.concluidaAluno()).isTrue();
        // E indica pendência separadamente
        assertThat(sessaoDTO.temPendenciasPersonal()).isTrue();
        assertThat(sessaoDTO.quantidadePendenciasPersonal()).isEqualTo(1);
        assertThat(sessaoDTO.metricasPendentes()).hasSize(1);
        assertThat(sessaoDTO.metricasPendentes().get(0).metricaId()).isEqualTo(metricaPersonal.getId());
    }

    @Test
    @DisplayName("US24 Cenário 4 e 5: Notificação agrupada por aluno com contagem de pendências atualizada")
    void US24_cenarios4_e_5_notificacaoAgrupadaPorAlunoComContagemAtualizada() {
        // Dado: notificação já existente para o aluno
        Notificacao notificacaoExistente = Notificacao.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioPersonal)
                .titulo("Pendências de Métricas - Aluno Lucas")
                .mensagem("O aluno Aluno Lucas possui 5 métrica(s) pendente(s) de preenchimento.")
                .tipo(TipoNotificacao.METRICAS_PENDENTES)
                .referenciaId(aluno.getId())
                .lida(false)
                .build();

        SessaoTreino sessao = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personalTrainer.getId(), aluno.getId()))
                .thenReturn(List.of(rotinaTreino));
        when(sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaTreino.getId()))
                .thenReturn(List.of(sessao));
        when(notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(usuarioPersonal.getId(), TipoNotificacao.METRICAS_PENDENTES, aluno.getId()))
                .thenReturn(Optional.of(notificacaoExistente));

        // Quando: consulta e atualiza
        acompanhamentoTreinoService.obterAcompanhamentoAluno(usuarioPersonal, aluno.getId());

        // Então: a notificação existente é atualizada com a nova contagem (1) em vez de criar uma duplicada
        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(captor.capture());

        Notificacao salva = captor.getValue();
        assertThat(salva.getId()).isEqualTo(notificacaoExistente.getId());
        assertThat(salva.getMensagem()).contains("possui 1 métrica(s) pendente(s)");
    }

    @Test
    @DisplayName("US24 Cenário 6: Zerou pendências remove a notificação do personal e atualiza indicador para 0")
    void US24_cenario6_zerouPendenciasRemoveNotificacaoEAtualizaIndicador() {
        // Dado: todas as métricas preenchidas (zero pendências)
        SessaoTreino sessao = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .dataPlanejada(LocalDate.of(2026, 10, 20))
                .status(StatusSessaoTreino.CONCLUIDA)
                .build();

        Desempenho desempenhoCompleto = Desempenho.builder()
                .id(UUID.randomUUID())
                .sessaoTreino(sessao)
                .registrosMetricas(new ArrayList<>(List.of(
                        RegistroMetrica.builder()
                                .metrica(metricaPersonal)
                                .valor("Técnica excelente de pernas")
                                .build(),
                        RegistroMetrica.builder()
                                .metrica(metricaAluno)
                                .valor("00:55")
                                .build()
                )))
                .build();
        sessao.setDesempenho(desempenhoCompleto);

        Notificacao notificacaoAntiga = Notificacao.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioPersonal)
                .tipo(TipoNotificacao.METRICAS_PENDENTES)
                .referenciaId(aluno.getId())
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personalTrainer.getId()))
                .thenReturn(Optional.of(aluno));
        when(rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personalTrainer.getId(), aluno.getId()))
                .thenReturn(List.of(rotinaTreino));
        when(sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaTreino.getId()))
                .thenReturn(List.of(sessao));
        when(notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(usuarioPersonal.getId(), TipoNotificacao.METRICAS_PENDENTES, aluno.getId()))
                .thenReturn(Optional.of(notificacaoAntiga));

        // Quando: calcula acompanhamento
        AcompanhamentoAlunoResponseDTO response = acompanhamentoTreinoService.obterAcompanhamentoAluno(usuarioPersonal, aluno.getId());

        // Então: quantidade de pendências é 0 e a notificação é removida
        assertThat(response.quantidadeMetricasPendentes()).isEqualTo(0);
        verify(notificacaoRepository).delete(notificacaoAntiga);
    }

    @Test
    @DisplayName("US24 Cenário 7: Apenas visualizar a notificação não resolve a pendência (apenas marca lida=true)")
    void US24_cenario7_visualizarNotificacaoNaoResolvePendencia() {
        UUID notifId = UUID.randomUUID();
        Notificacao notif = Notificacao.builder()
                .id(notifId)
                .usuario(usuarioPersonal)
                .titulo("Pendências de Métricas - Aluno Lucas")
                .mensagem("O aluno possui pendências.")
                .tipo(TipoNotificacao.METRICAS_PENDENTES)
                .referenciaId(aluno.getId())
                .lida(false)
                .build();

        when(notificacaoRepository.findById(notifId)).thenReturn(Optional.of(notif));
        when(notificacaoRepository.save(any(Notificacao.class))).thenAnswer(i -> i.getArgument(0));

        // Quando: marca como lida
        NotificacaoResponseDTO response = acompanhamentoTreinoService.marcarComoLida(usuarioPersonal, notifId);

        // Então: lida passa para true, mas a notificação NÃO é deletada
        assertThat(response.lida()).isTrue();
        verify(notificacaoRepository, never()).delete(any(Notificacao.class));
    }

    @Test
    @DisplayName("US24 Cenário 3 e 4: Resumo consolidado de pendências para múltiplos alunos")
    void US24_cenario3_e_4_resumoConsolidadoDePendencias() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));
        when(alunoRepository.findByPersonalId(personalTrainer.getId())).thenReturn(List.of(aluno));
        when(rotinaTreinoRepository.findByPersonalTrainerIdAndAlunosId(personalTrainer.getId(), aluno.getId()))
                .thenReturn(List.of(rotinaTreino));

        SessaoTreino s1 = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotinaTreino)
                .semana(1)
                .status(StatusSessaoTreino.PLANEJADA)
                .build();

        when(sessaoTreinoRepository.findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(rotinaTreino.getId()))
                .thenReturn(List.of(s1));
        when(notificacaoRepository.findByUsuarioIdAndTipoAndReferenciaId(usuarioPersonal.getId(), TipoNotificacao.METRICAS_PENDENTES, aluno.getId()))
                .thenReturn(Optional.empty());

        ResumoPendenciasResponseDTO resumo = acompanhamentoTreinoService.obterResumoPendencias(usuarioPersonal);

        assertThat(resumo.totalAlunosComPendencias()).isEqualTo(1);
        assertThat(resumo.totalMetricasPendentes()).isEqualTo(1);
        assertThat(resumo.alunos().get(0).nomeAluno()).isEqualTo("Aluno Lucas");
        assertThat(resumo.alunos().get(0).quantidadePendencias()).isEqualTo(1);
    }
}
