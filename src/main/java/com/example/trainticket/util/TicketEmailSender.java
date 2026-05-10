package com.example.trainticket.util;

import com.example.trainticket.dto.EmailRequest;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Date;

import static com.example.trainticket.util.Constants.*;

@Component
@RequiredArgsConstructor
public class TicketEmailSender {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailSender.class);
    private final JavaMailSender mailSender;

    public void sendEmail(EmailRequest request) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();

            msg.setFrom(new InternetAddress(SENDER_ADDRESS, SENDER_DISPLAY_NAME));
            msg.setReplyTo(InternetAddress.parse(SENDER_ADDRESS, false));
            msg.setSubject(request.subject(), "UTF-8");
            msg.setSentDate(new Date());
            msg.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(request.toEmail(), false));

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(request.htmlContent(), "text/html; charset=utf-8");

            MimeBodyPart jsonPart = new MimeBodyPart();
            jsonPart.setContent(request.jsonTicket(), "application/json");
            jsonPart.setFileName(TICKET_EMAIL_NAME);

            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(jsonPart);

            msg.setContent(multipart);

            mailSender.send(msg);
            log.info("Confirmation email sent to {}", request.toEmail());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", request.toEmail(), e.getMessage());
        }
    }
}
