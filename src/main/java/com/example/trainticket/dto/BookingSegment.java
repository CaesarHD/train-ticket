package com.example.trainticket.dto;

import jakarta.validation.constraints.NotBlank;

public record BookingSegment(
        @NotBlank(message = "trainCode is required") String trainCode,
        @NotBlank(message = "departureStation is required") String departureStation,
        @NotBlank(message = "arrivalStation is required") String arrivalStation
) {}
