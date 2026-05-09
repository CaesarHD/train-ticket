package com.example.trainticket.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        List<SegmentResponse> segments,
        LocalDate travelDate,
        LocalDateTime createdAt,
        String userName,
        String userEmail
) {}
