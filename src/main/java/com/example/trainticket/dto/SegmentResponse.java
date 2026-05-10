package com.example.trainticket.dto;

public record SegmentResponse(
        String trainCode,
        String departureStation,
        String arrivalStation,
        String departureTime,
        String arrivalTime
) {}
