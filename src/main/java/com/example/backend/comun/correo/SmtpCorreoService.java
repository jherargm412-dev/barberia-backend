package com.example.backend.comun.correo;

import com.example.backend.exception.CorreoNoEnviadoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envía correos por SMTP (Gmail del proyecto, ver spring.mail.* en application.properties).
 * Si MAIL_USERNAME no está configurado, no envía nada y escribe el mensaje en el log: así cada
 * integrante puede probar en su máquina sin tener la contraseña de aplicación.
 */
@Slf4j
@Service
public class SmtpCorreoService implements CorreoService {

    private final JavaMailSender mailSender;
    private final String remitente;
    private final String remitenteNombre;

    public SmtpCorreoService(JavaMailSender mailSender,
                             @Value("${spring.mail.username:}") String remitente,
                             @Value("${app.correo.remitente-nombre:House of Cut}") String remitenteNombre) {
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.remitenteNombre = remitenteNombre;
    }

    @Override
    public void enviar(String para, String asunto, String cuerpo) {
        if (remitente.isBlank()) {
            log.warn("[CORREO NO CONFIGURADO: falta MAIL_USERNAME en .env] Para: {} | Asunto: {}\n{}", para, asunto, cuerpo);
            return;
        }
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitenteNombre + " <" + remitente + ">");
        mensaje.setTo(para);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        try {
            mailSender.send(mensaje);
            log.info("Correo enviado a {} ({})", para, asunto);
        } catch (MailException ex) {
            log.error("No se pudo enviar el correo a {}: {}", para, ex.getMessage());
            throw new CorreoNoEnviadoException();
        }
    }
}
