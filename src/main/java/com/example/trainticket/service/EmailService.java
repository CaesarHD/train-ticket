package com.example.trainticket.service;

import com.example.trainticket.dto.EmailRequest;
import com.example.trainticket.util.TicketEmailSender;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final TicketEmailSender emailSender;

    @Async
    public void sendConfirmation(String toEmail, String userName, Long itineraryId, String jsonData) {
        try {
            String html = String.format("<html><body>Hi %s, your ticket is ready!</body></html>", userName);

            EmailRequest emailRequest = new EmailRequest(
                    toEmail,
                    "Booking Confirmation: " + itineraryId,
                    html,
                    jsonData
            );

            emailSender.sendEmail(emailRequest);
            log.info("Confirmation email queued for {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }
}
