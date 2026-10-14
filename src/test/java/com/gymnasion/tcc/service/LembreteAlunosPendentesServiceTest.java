package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.Notificacao;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.event.LembreteAlunosPendentesEmailListener;
import com.gymnasion.tcc.event.LembreteAlunosPendentesEvent;
import com.gymnasion.tcc.infra.config.AlunosPendentesProperties;
import com.gymnasion.tcc.repository.AlunoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LembreteAlunosPendentesServiceTest {

    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private NotificacaoService notificacaoService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AlunosPendentesProperties properties;
    private LembreteAlunosPendentesService lembreteService;

    private PersonalTrainer personal;
    private Usuario usuarioPersonal;
    private Aluno aluno1;
    private Aluno aluno2;

    @BeforeEach
    void setUp() {
        properties = new AlunosPendentesProperties(true, Duration.ofHours(24), Duration.ofHours(24), "0 0 * * * *");
        lembreteService = new LembreteAlunosPendentesService(alunoRepository, notificacaoService, eventPublisher, properties);

        usuarioPersonal = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Personal Teste")
                .email("personal@teste.com")
                .build();

        personal = PersonalTrainer.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioPersonal)
                .build();

        Usuario u1 = Usuario.builder().id(UUID.randomUUID()).nome("Bruna Carvalho").email("bruna@teste.com").build();
        aluno1 = Aluno.builder().id(UUID.randomUUID()).personal(personal).usuario(u1).status(StatusConvite.PENDENTE).build();

        Usuario u2 = Usuario.builder().id(UUID.randomUUID()).nome("Carlos Silva").email("carlos@teste.com").build();
        aluno2 = Aluno.builder().id(UUID.randomUUID()).personal(personal).usuario(u2).status(StatusConvite.PENDENTE).build();
    }

    @Test
    @DisplayName("Gera lembrete in-app e dispara e-mail quando há alunos pendentes há mais de 24h")
    void processarLembretes_alunosPendentes_geraLembreteEEmail() {
        when(alunoRepository.findByStatusCadastradosAntesDe(eq(StatusConvite.PENDENTE), any(OffsetDateTime.class)))
                .thenReturn(List.of(aluno1, aluno2));
        when(notificacaoService.buscarLembreteAlunosPendentes(personal)).thenReturn(Optional.empty());

        int processados = lembreteService.processarLembretes();

        assertThat(processados).isEqualTo(1);
        verify(notificacaoService).registrarLembreteAlunosPendentes(eq(personal), eq(2), eq(Duration.ofHours(24)));

        ArgumentCaptor<LembreteAlunosPendentesEvent> captor = ArgumentCaptor.forClass(LembreteAlunosPendentesEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().emailPersonal()).isEqualTo("personal@teste.com");
        assertThat(captor.getValue().nomesAlunos()).containsExactly("Bruna Carvalho", "Carlos Silva");
    }

    @Test
    @DisplayName("Não reenvia lembrete se o personal já foi lembrado recentemente dentro do intervalo")
    void processarLembretes_jaLembradoRecentemente_ignora() {
        when(alunoRepository.findByStatusCadastradosAntesDe(eq(StatusConvite.PENDENTE), any(OffsetDateTime.class)))
                .thenReturn(List.of(aluno1));

        Notificacao lembreteRecente = Notificacao.builder()
                .id(UUID.randomUUID())
                .dataCriacao(OffsetDateTime.now().minusHours(2)) // lembrado há apenas 2h
                .build();
        when(notificacaoService.buscarLembreteAlunosPendentes(personal)).thenReturn(Optional.of(lembreteRecente));

        int processados = lembreteService.processarLembretes();

        assertThat(processados).isEqualTo(0);
        verify(notificacaoService, never()).registrarLembreteAlunosPendentes(any(), anyInt(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Não faz nada se a propriedade estiver desabilitada")
    void processarLembretes_desabilitado_naoFazNada() {
        AlunosPendentesProperties propsDesabilitado = new AlunosPendentesProperties(false, Duration.ofHours(24), Duration.ofHours(24), "");
        LembreteAlunosPendentesService service = new LembreteAlunosPendentesService(alunoRepository, notificacaoService, eventPublisher, propsDesabilitado);

        int processados = service.processarLembretes();

        assertThat(processados).isEqualTo(0);
        verifyNoInteractions(alunoRepository, notificacaoService, eventPublisher);
    }

    @Test
    @DisplayName("Listener encaminha evento para o EmailService")
    void listener_encaminhaParaEmailService() {
        EmailService emailService = mock(EmailService.class);
        LembreteAlunosPendentesEmailListener listener = new LembreteAlunosPendentesEmailListener(emailService);

        LembreteAlunosPendentesEvent event = new LembreteAlunosPendentesEvent(
                "personal@teste.com",
                "Personal Teste",
                List.of("Aluno 1"),
                "24 horas"
        );

        listener.onLembrete(event);

        verify(emailService).notificarAlunosPendentes("personal@teste.com", "Personal Teste", List.of("Aluno 1"), "24 horas");
    }
}
