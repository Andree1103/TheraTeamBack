package com.therateam.therateam.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envía correos vía SMTP si `spring.mail.host` está configurado (ver application.properties —
 * variables MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD). Si no hay SMTP configurado (ej. desarrollo
 * local sin esas variables), en vez de fallar registra el contenido en el log — así el flujo de
 * "olvidé mi contraseña" se puede probar en local sin depender de un proveedor de correo real.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${MAIL_FROM:no-reply@therateam.com}")
    private String from;

    /** Aunque MAIL_HOST esté vacío, Spring igual crea el bean JavaMailSenderImpl — por eso se
     *  chequea explícitamente esta propiedad en vez de confiar en que el bean exista o no. */
    @Value("${spring.mail.host:}")
    private String mailHost;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void enviarCorreo(String destinatario, String asunto, String cuerpo) {
        if (mailHost == null || mailHost.isBlank()) {
            log.warn("SMTP no configurado (MAIL_HOST vacío) — correo simulado para {} | Asunto: {}\n{}", destinatario, asunto, cuerpo);
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("SMTP no configurado — correo simulado para {} | Asunto: {}\n{}", destinatario, asunto, cuerpo);
            return;
        }
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(from);
            mensaje.setTo(destinatario);
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            mailSender.send(mensaje);
        } catch (Exception e) {
            log.error("Error enviando correo a {}: {}", destinatario, e.getMessage());
        }
    }
}
