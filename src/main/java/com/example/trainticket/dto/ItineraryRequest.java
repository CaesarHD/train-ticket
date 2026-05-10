package com.example.trainticket.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record ItineraryRequest(
   @NotNull(message = "segments is required")
   List<BookingSegment> segments,
   @NotNull(message = "travelDate is required") LocalDate travelDate,
   @NotBlank(message = "userEmail is required") String userEmail,
   @NotBlank(message = "userName is required") String userName
) {}
