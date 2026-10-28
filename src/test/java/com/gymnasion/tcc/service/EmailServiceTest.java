package com.gymnasion.tcc.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "mailFrom", "Gymnasion <nao-responda@gymnasion.com.br>");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://gymnasion.com.br");
        emailService.carregarTemplates();
    }

    @Test
    @DisplayName("Deve enviar e-mail simples com sucesso")
    void enviarEmail_sucesso() {
        emailService.enviarEmail("aluno@gymnasion.com", "Assunto Teste", "Corpo da mensagem");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("Gymnasion <nao-responda@gymnasion.com.br>");
        assertThat(sent.getTo()).containsExactly("aluno@gymnasion.com");
        assertThat(sent.getSubject()).isEqualTo("Assunto Teste");
        assertThat(sent.getText()).isEqualTo("Corpo da mensagem");
    }

    @Test
    @DisplayName("Não deve lançar exceção quando o JavaMailSender for nulo")
    void enviarEmail_quandoMailSenderForNulo_naoLancaExcecao() {
        EmailService serviceSemSender = new EmailService(null);

        assertThatCode(() -> serviceSemSender.enviarEmail("aluno@gymnasion.com", "Assunto", "Mensagem"))
                .doesNotThrowAnyException();

        assertThatCode(() -> serviceSemSender.enviarEmailHtmlComLogo("aluno@gymnasion.com", "Assunto", "<html></html>", "Mensagem"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Deve capturar e logar exceção caso ocorra falha de envio sem propagar erro")
    void enviarEmail_quandoOcorreErro_naoPropagaExcecao() {
        doThrow(new MailSendException("Erro de conexão SMTP")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailService.enviarEmail("aluno@gymnasion.com", "Assunto", "Mensagem"))
                .doesNotThrowAnyException();

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("US-03 C1: Deve enviar convite HTML estilizado com link e logo inline")
    void enviarConviteAluno_sucesso() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.enviarConviteAluno("aluno@gymnasion.com", "Professor Silva", "Natação", "https://gymnasion.com.br/convite/token123");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("US-03 C1: Deve fazer fallback para e-mail texto puro se template não estiver carregado")
    void enviarConviteAluno_fallbackTextoPuro() {
        ReflectionTestUtils.setField(emailService, "templateConviteHtml", null);

        emailService.enviarConviteAluno("aluno@gymnasion.com", "Professor Silva", "Natação", "https://gymnasion.com.br/convite/token123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("aluno@gymnasion.com");
        assertThat(sent.getSubject()).isEqualTo("Você foi convidado(a) para treinar no Gymnasion");
        assertThat(sent.getText()).contains("Natação");
        assertThat(sent.getText()).contains("https://gymnasion.com.br/convite/token123");
        assertThat(sent.getText()).contains("15 minutos");
    }

    @Test
    @DisplayName("US-03 C3: Deve enviar e-mail de aprovação ao aluno com texto literal do DRS")
    void notificarAprovacaoAluno_sucesso() {
        emailService.notificarAprovacaoAluno("aluno@gymnasion.com", "Professor Silva");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("aluno@gymnasion.com");
        assertThat(sent.getSubject()).isEqualTo("Gymnasion - Solicitação Aprovada");
        assertThat(sent.getText()).isEqualTo("O Professor Silva aprovou sua solicitação, agora você pode acessar o sistema para conferir seus treinos.");
    }

    @Test
    @DisplayName("US-03 C4: Deve enviar e-mail de recusa ao aluno com texto literal do DRS")
    void notificarRecusaAluno_sucesso() {
        emailService.notificarRecusaAluno("aluno@gymnasion.com", "Professor Silva");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("aluno@gymnasion.com");
        assertThat(sent.getSubject()).isEqualTo("Gymnasion - Solicitação Recusada");
        assertThat(sent.getText()).isEqualTo("O Professor Silva recusou sua solicitação.");
    }

    @Test
    @DisplayName("Deve enviar lembrete ao personal listando alunos pendentes")
    void notificarAlunosPendentes_sucesso() {
        emailService.notificarAlunosPendentes("personal@gymnasion.com", "Carlos", List.of("Aluno 1", "Aluno 2"), "24 horas");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("personal@gymnasion.com");
        assertThat(sent.getSubject()).contains("alunos aguardando aprovação");
        assertThat(sent.getText()).contains("2 aluno(s)");
        assertThat(sent.getText()).contains("Aluno 1");
        assertThat(sent.getText()).contains("Aluno 2");
        assertThat(sent.getText()).contains("https://gymnasion.com.br");
    }
}
