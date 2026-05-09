package com.example.trainticket.dto;

public record SegmentResponse(
        String trainCode,
        String departureStation,
        String destinationStation,
        String departureTime,
        String arrivalTime
) {}
