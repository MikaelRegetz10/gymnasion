package com.gymnasion.tcc.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@gymnasion.local}")
    private String mailFrom;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private String templateConviteHtml;

    @Autowired
    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostConstruct
    public void carregarTemplates() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/email/convite-aluno.html");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    this.templateConviteHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    log.info("Template de e-mail de convite carregado com sucesso.");
                }
            } else {
                log.warn("Arquivo de template 'templates/email/convite-aluno.html' não encontrado no classpath.");
            }
        } catch (Exception e) {
            log.error("Erro ao carregar template de convite: {}", e.getMessage(), e);
        }
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
     * Envia e-mail em formato HTML com imagem inline (CID) e versão alternativa em texto puro.
     */
    public void enviarEmailHtmlComLogo(String para, String assunto, String htmlBody, String fallbackTexto) {
        if (mailSender == null) {
            log.warn("JavaMailSender não está configurado. E-mail para [{}] não enviado.", para);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(para);
            helper.setSubject(assunto);

            if (fallbackTexto != null && !fallbackTexto.isBlank()) {
                helper.setText(fallbackTexto, htmlBody);
            } else {
                helper.setText(htmlBody, true);
            }

            ClassPathResource logoResource = new ClassPathResource("images/logo.png");
            if (logoResource.exists()) {
                helper.addInline("logo", logoResource, "image/png");
            } else {
                log.warn("Logo para e-mail não encontrada em classpath:images/logo.png");
            }

            mailSender.send(mimeMessage);
            log.info("E-mail HTML enviado com sucesso para [{}]: {}", para, assunto);
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail HTML para [{}]: {}. Assunto: {}", para, e.getMessage(), assunto);
        }
    }

    /**
     * US-03 C1: Envio de link de convite ao aluno por e-mail com template visual estilizado.
     */
    public void enviarConviteAluno(String emailAluno, String nomePersonal, String nomeModalidade, String linkConvite) {
        String assunto = "Você foi convidado(a) para treinar no Gymnasion";
        String fallbackTexto = "Olá!\n\n"
                + "Você foi convidado(a) por " + nomePersonal + " para treinar " + nomeModalidade + " na plataforma Gymnasion.\n\n"
                + "Para concluir seu cadastro, acesse o link abaixo (válido por 15 minutos):\n"
                + linkConvite + "\n\n"
                + "Se você não solicitou este convite, por favor desconsidere este e-mail.\n\n"
                + "Equipe Gymnasion";

        if (templateConviteHtml != null && !templateConviteHtml.isBlank()) {
            String html = templateConviteHtml
                    .replace("{{nomePersonal}}", HtmlUtils.htmlEscape(nomePersonal != null ? nomePersonal : ""))
                    .replace("{{modalidade}}", HtmlUtils.htmlEscape(nomeModalidade != null ? nomeModalidade : ""))
                    .replace("{{link}}", linkConvite != null ? linkConvite : "");

            enviarEmailHtmlComLogo(emailAluno, assunto, html, fallbackTexto);
        } else {
            enviarEmail(emailAluno, assunto, fallbackTexto);
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
