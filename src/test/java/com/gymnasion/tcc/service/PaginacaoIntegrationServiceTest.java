package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoRegistroPerfil;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaginacaoIntegrationServiceTest {

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;
    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private RotinaTreinoRepository rotinaTreinoRepository;
    @Mock
    private SessaoTreinoRepository sessaoTreinoRepository;
    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private ObservacaoFeedbackRepository observacaoFeedbackRepository;
    @Mock
    private MetricaRepository metricaRepository;
    @Mock
    private RegistroMetricaRepository registroMetricaRepository;
    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private AuditoriaDesempenhoRepository auditoriaDesempenhoRepository;

    @InjectMocks
    private RotinaTreinoService rotinaTreinoService;
    @InjectMocks
    private TurmaService turmaService;
    @InjectMocks
    private ObservacaoFeedbackService observacaoFeedbackService;
    @InjectMocks
    private MetricaService metricaService;
    @InjectMocks
    private NotificacaoService notificacaoService;
    @InjectMocks
    private CorrecaoDesempenhoService correcaoDesempenhoService;

    private Usuario usuarioPersonal;
    private PersonalTrainer personal;
    private Usuario usuarioAluno;
    private Aluno aluno;
    private RotinaTreino rotina;
    private Modalidade modalidade;

    @BeforeEach
    void setUp() {
        usuarioPersonal = Usuario.builder().id(UUID.randomUUID()).nome("Carlos Personal").email("carlos@teste.com").role(Role.PERSONAL_TRAINER).build();
        personal = PersonalTrainer.builder().id(UUID.randomUUID()).usuario(usuarioPersonal).build();

        usuarioAluno = Usuario.builder().id(UUID.randomUUID()).nome("Lucas Aluno").email("lucas@teste.com").role(Role.ALUNO).build();
        aluno = Aluno.builder().id(UUID.randomUUID()).usuario(usuarioAluno).personal(personal).status(StatusConvite.ATIVO).build();

        modalidade = Modalidade.builder().id(1L).nome("Musculação").build();
        rotina = RotinaTreino.builder().id(UUID.randomUUID()).titulo("Rotina Hipertrofia").frequenciaSemanal(3).personalTrainer(personal).modalidade(modalidade).alunos(new HashSet<>(List.of(aluno))).build();
    }

    @Test
    @DisplayName("RotinaTreinoService: listarPorPersonal paginado retorna Page correta")
    void rotinaTreinoService_listarPorPersonal_paginado() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personal));
        Pageable pageable = PageRequest.of(0, 10);
        Page<RotinaTreino> pageMock = new PageImpl<>(List.of(rotina), pageable, 1);
        when(rotinaTreinoRepository.findByPersonalTrainerId(personal.getId(), pageable)).thenReturn(pageMock);

        Page<RotinaTreinoResponseDTO> resultado = rotinaTreinoService.listarPorPersonal(usuarioPersonal, pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).titulo()).isEqualTo("Rotina Hipertrofia");
    }

    @Test
    @DisplayName("RotinaTreinoService: consultarHistoricoRotina paginado retorna Page correta")
    void rotinaTreinoService_consultarHistoricoRotina_paginado() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personal));
        when(rotinaTreinoRepository.findByIdAndPersonalTrainerId(rotina.getId(), personal.getId())).thenReturn(Optional.of(rotina));

        SessaoTreino sessao = SessaoTreino.builder()
                .id(UUID.randomUUID())
                .rotinaTreino(rotina)
                .status(StatusSessaoTreino.CONCLUIDA)
                .dataReal(LocalDate.now())
                .build();

        Pageable pageable = PageRequest.of(0, 5);
        Page<SessaoTreino> pageMock = new PageImpl<>(List.of(sessao), pageable, 1);
        when(sessaoTreinoRepository.findByRotinaTreinoIdAndStatusOrderByDataRealDesc(rotina.getId(), StatusSessaoTreino.CONCLUIDA, pageable))
                .thenReturn(pageMock);

        Page<SessaoTreinoResponseDTO> resultado = rotinaTreinoService.consultarHistoricoRotina(usuarioPersonal, rotina.getId(), pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).status()).isEqualTo(StatusSessaoTreino.CONCLUIDA);
    }

    @Test
    @DisplayName("TurmaService: listarTurmas paginado retorna Page correta")
    void turmaService_listarTurmas_paginado() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personal));
        Turma turma = Turma.builder().id(UUID.randomUUID()).nome("Turma Manhã").personal(personal).modalidade(modalidade).alunos(new HashSet<>()).build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Turma> pageMock = new PageImpl<>(List.of(turma), pageable, 1);
        when(turmaRepository.findByPersonalId(personal.getId(), pageable)).thenReturn(pageMock);

        Page<TurmaResponseDTO> resultado = turmaService.listarTurmas(usuarioPersonal, pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).nome()).isEqualTo("Turma Manhã");
    }

    @Test
    @DisplayName("ObservacaoFeedbackService: listarObservacoesEFeedbacksPeloPersonal paginado retorna Page correta")
    void observacaoFeedbackService_listar_paginado() {
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personal));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personal.getId())).thenReturn(Optional.of(aluno));

        ObservacaoFeedback obs = ObservacaoFeedback.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .autor(usuarioPersonal)
                .tipo(TipoRegistroPerfil.FEEDBACK)
                .conteudo("Excelente evolução!")
                .dataCriacao(OffsetDateTime.now())
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<ObservacaoFeedback> pageMock = new PageImpl<>(List.of(obs), pageable, 1);
        when(observacaoFeedbackRepository.findByAlunoIdOrderByDataCriacaoDesc(aluno.getId(), pageable)).thenReturn(pageMock);

        Page<ObservacaoFeedbackResponseDTO> resultado = observacaoFeedbackService.listarObservacoesEFeedbacksPeloPersonal(usuarioPersonal, aluno.getId(), pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).conteudo()).isEqualTo("Excelente evolução!");
    }

    @Test
    @DisplayName("NotificacaoService: listarPorUsuario paginado retorna Page correta")
    void notificacaoService_listarPorUsuario_paginado() {
        Notificacao notif = Notificacao.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .titulo("Treino atribuído")
                .mensagem("Você recebeu um novo treino.")
                .dataCriacao(OffsetDateTime.now())
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Notificacao> pageMock = new PageImpl<>(List.of(notif), pageable, 1);
        when(notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioAluno.getId(), pageable)).thenReturn(pageMock);

        Page<NotificacaoResponseDTO> resultado = notificacaoService.listarPorUsuario(usuarioAluno, pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).titulo()).isEqualTo("Treino atribuído");
    }
}
