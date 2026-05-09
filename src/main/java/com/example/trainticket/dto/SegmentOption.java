package com.example.trainticket.dto;

public record SegmentOption(
        String trainCode,
        String departureStation,
        String arrivalStation,
        String departureTime,
        String arrivalTime
) {}
