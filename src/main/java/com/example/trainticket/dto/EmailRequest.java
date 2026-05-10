package com.example.trainticket.dto;

public record EmailRequest(
    String toEmail,
    String subject,
    String htmlContent,
    String jsonTicket
) {}