package br.com.datalume.alerta.aplicacao;

import br.com.datalume.alerta.AlertaProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class EnviadorEmailSmtp implements EnviadorEmail {

    private final JavaMailSender mail;
    private final AlertaProperties props;

    EnviadorEmailSmtp(JavaMailSender mail, AlertaProperties props) {
        this.mail = mail;
        this.props = props;
    }

    @Override
    public void enviar(MensagemEmail m) {
        try {
            MimeMessage mime = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(props.remetente());
            helper.setTo(m.para());
            helper.setSubject(m.assunto());
            helper.setText(m.texto(), m.html());
            mime.addHeader("List-Unsubscribe", "<" + m.linkCancelamento() + ">");
            mail.send(mime);
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
    }
}