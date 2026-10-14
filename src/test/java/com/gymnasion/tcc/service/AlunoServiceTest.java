package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.Notificacao;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.AlunoResponseDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.event.SolicitacaoAlunoAvaliadaEvent;
import com.gymnasion.tcc.event.SolicitacaoAlunoEmailListener;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.NotificacaoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlunoServiceTest {

    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private PersonalTrainerRepository personalTrainerRepository;
    @Mock
    private NotificacaoService notificacaoService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlunoService alunoService;

    private Usuario usuarioPersonal;
    private PersonalTrainer personal;
    private Aluno aluno;

    @BeforeEach
    void setUp() {
        usuarioPersonal = Usuario.builder().id(UUID.randomUUID()).nome("Carlos Personal").email("carlos@teste.com").build();
        personal = PersonalTrainer.builder().id(UUID.randomUUID()).usuario(usuarioPersonal).build();

        Usuario usuarioAluno = Usuario.builder().id(UUID.randomUUID()).nome("João Aluno").email("joao@teste.com").build();
        aluno = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .personal(personal)
                .status(StatusConvite.PENDENTE)
                .modalidades(new HashSet<>())
                .build();

        lenient().when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personal));
        lenient().when(alunoRepository.findById(aluno.getId())).thenReturn(Optional.of(aluno));
    }

    @Test
    @DisplayName("US-03 C3: aprovar aluno ativa, cria notificação e dispara e-mail automático")
    void aprovarAluno_disparaEmailENotificacao() {
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> inv.getArgument(0));

        AlunoResponseDTO response = alunoService.aprovarAluno(aluno.getId(), usuarioPersonal);

        assertThat(response.status()).isEqualTo(StatusConvite.ATIVO);

        verify(notificacaoService).notificarSolicitacaoAprovada(aluno, "Carlos Personal");
        verify(notificacaoService).resolverSolicitacao(eq(personal), eq(aluno.getId()), anyBoolean());

        ArgumentCaptor<SolicitacaoAlunoAvaliadaEvent> eventCaptor = ArgumentCaptor.forClass(SolicitacaoAlunoAvaliadaEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().aprovado()).isTrue();
        assertThat(eventCaptor.getValue().emailAluno()).isEqualTo("joao@teste.com");
        assertThat(eventCaptor.getValue().nomePersonal()).isEqualTo("Carlos Personal");
    }

    @Test
    @DisplayName("US-03 C4: recusar aluno remove registro, dispara e-mail e retorna mensagem do DRS")
    void recusarAluno_disparaEmailERetornaMensagem() {
        MensagemResponseDTO response = alunoService.recusarAluno(aluno.getId(), usuarioPersonal);

        assertThat(response.mensagem()).isEqualTo("Aluno João Aluno foi recusado com sucesso!");
        verify(alunoRepository).delete(aluno);
        verify(notificacaoService).resolverSolicitacao(eq(personal), eq(aluno.getId()), anyBoolean());

        ArgumentCaptor<SolicitacaoAlunoAvaliadaEvent> eventCaptor = ArgumentCaptor.forClass(SolicitacaoAlunoAvaliadaEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().aprovado()).isFalse();
        assertThat(eventCaptor.getValue().emailAluno()).isEqualTo("joao@teste.com");
    }

    @Test
    @DisplayName("Não dispara e-mail quando o aluno não está pendente")
    void aprovarAluno_naoPendente_naoDisparaEmail() {
        aluno.setStatus(StatusConvite.ATIVO);

        assertThatThrownBy(() -> alunoService.aprovarAluno(aluno.getId(), usuarioPersonal))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(eventPublisher, notificacaoService);
    }

    @Test
    @DisplayName("Listener envia o e-mail correto conforme aprovação/recusa")
    void listener_encaminhaParaEmailService() {
        EmailService emailService = mock(EmailService.class);
        SolicitacaoAlunoEmailListener listener = new SolicitacaoAlunoEmailListener(emailService);

        listener.onSolicitacaoAvaliada(new SolicitacaoAlunoAvaliadaEvent("a@a.com", "A", "P", true));
        listener.onSolicitacaoAvaliada(new SolicitacaoAlunoAvaliadaEvent("b@b.com", "B", "P", false));

        verify(emailService).notificarAprovacaoAluno("a@a.com", "P");
        verify(emailService).notificarRecusaAluno("b@b.com", "P");
    }
}
