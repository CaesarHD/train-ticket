package com.example.trainticket.dto;

import java.time.LocalDate;

public record BookingRequest (
   String trainCode,
   String departureStation,
    String destinationStation,
   LocalDate travelDate,
   String userEmail,
   String userName
) {}
