package com.example.trainticket.util;

import com.example.trainticket.dto.EmailRequest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketEmailSenderTest {

    @Mock
    JavaMailSender mailSender;

    @Mock
    MimeMessage mimeMessage;

    @InjectMocks
    TicketEmailSender emailSender;

    @Captor
    ArgumentCaptor<MimeMessage> messageCaptor;

    @Test
    void sendEmail_createsAndSendsMimeMessage() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        EmailRequest request = new EmailRequest("user@test.com", "Booking Confirmation: 1", "<html>body</html>", "{\"id\":1}");

        emailSender.sendEmail(request);

        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isSameAs(mimeMessage);
    }

    @Test
    void sendEmail_handlesExceptionGracefully() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP down"));

        EmailRequest request = new EmailRequest("user@test.com", "Subject", "<html>body</html>", "{}");

        emailSender.sendEmail(request);
    }
}
