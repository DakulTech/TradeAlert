package com.tradealert.verificationservice.service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class EmailSender {

    public void send(String to, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", System.getenv().getOrDefault("SMTP_HOST", "smtp.example.com"));
        props.put("mail.smtp.port", System.getenv().getOrDefault("SMTP_PORT", "587"));

        String username = System.getenv().getOrDefault("SMTP_USER", "user@example.com");
        String password = System.getenv().getOrDefault("SMTP_PASS", "password");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(body);

        Transport.send(message);
    }
}
