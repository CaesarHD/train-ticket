package com.example.trainticket.dto;

public record BookingSegment(
        String trainCode,
        String departureStation,
        String destinationStation
) {}
