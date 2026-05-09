package com.example.trainticket.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        String trainCode,
        String departureStation,
        String destinationStation,
        LocalDate travelDate,
        LocalDateTime createdAt,
        String userName,
        String userEmail
) {}
