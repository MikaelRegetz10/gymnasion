package com.gymnasion.tcc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@gymnasion.local}")
    private String mailFrom;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Autowired
    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void enviarEmail(String para, String assunto, String texto) {
        if (mailSender == null) {
            log.warn("JavaMailSender não está configurado. E-mail para [{}] não enviado: {}", para, texto);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(para);
            message.setSubject(assunto);
            message.setText(texto);

            mailSender.send(message);
            log.info("E-mail enviado com sucesso para [{}]: {}", para, assunto);
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail para [{}]: {}. Mensagem: {}", para, e.getMessage(), texto);
        }
    }

    /**
     * US-03 C3: E-mail ao aluno aprovado.
     * Mensagem literal do DRS: "O {personal} aprovou sua solicitação, agora você pode acessar o sistema para conferir seus treinos."
     */
    public void notificarAprovacaoAluno(String emailAluno, String nomePersonal) {
        String assunto = "Gymnasion - Solicitação Aprovada";
        String mensagem = "O " + nomePersonal + " aprovou sua solicitação, agora você pode acessar o sistema para conferir seus treinos.";
        enviarEmail(emailAluno, assunto, mensagem);
    }

    /**
     * US-03 C4: E-mail ao aluno recusado.
     * Mensagem literal do DRS: "O {personal} recusou sua solicitação."
     */
    public void notificarRecusaAluno(String emailAluno, String nomePersonal) {
        String assunto = "Gymnasion - Solicitação Recusada";
        String mensagem = "O " + nomePersonal + " recusou sua solicitação.";
        enviarEmail(emailAluno, assunto, mensagem);
    }

    /**
     * Lembrete ao personal: alunos aguardando aprovação há mais que o tempo limite configurado.
     */
    public void notificarAlunosPendentes(String emailPersonal, String nomePersonal, List<String> nomesAlunos, String tempoLimite) {
        String assunto = "Gymnasion - Você possui alunos aguardando aprovação";

        StringBuilder texto = new StringBuilder()
                .append("Olá, ").append(nomePersonal).append("!\n\n")
                .append("Você possui ").append(nomesAlunos.size())
                .append(" aluno(s) aguardando sua aprovação há mais de ").append(tempoLimite).append(":\n\n");
        nomesAlunos.forEach(nome -> texto.append("  - ").append(nome).append("\n"));
        texto.append("\nAcesse o Gymnasion para aprovar ou recusar as solicitações: ")
                .append(frontendUrl).append("\n\n")
                .append("Equipe Gymnasion");

        enviarEmail(emailPersonal, assunto, texto.toString());
    }
}
