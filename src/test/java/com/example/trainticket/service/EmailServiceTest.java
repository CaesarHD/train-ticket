package com.example.trainticket.service;

import com.example.trainticket.dto.EmailRequest;
import com.example.trainticket.util.TicketEmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    TicketEmailSender emailSender;

    @InjectMocks
    EmailService emailService;

    @Captor
    ArgumentCaptor<EmailRequest> requestCaptor;

    @Test
    void sendConfirmation_delegatesToEmailSender() {
        emailService.sendConfirmation("alice@test.com", "Alice", 42L, "{\"id\":42}");

        verify(emailSender).sendEmail(requestCaptor.capture());

        EmailRequest sent = requestCaptor.getValue();
        assertThat(sent.toEmail()).isEqualTo("alice@test.com");
        assertThat(sent.subject()).contains("42");
        assertThat(sent.htmlContent()).contains("Alice");
        assertThat(sent.jsonTicket()).isEqualTo("{\"id\":42}");
    }
}
