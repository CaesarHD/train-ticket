package com.example.trainticket.dto;

import java.time.LocalDate;
import java.util.List;

public record BookingRequest (
   List<BookingSegment> segments,
   LocalDate travelDate,
   String userEmail,
   String userName
) {}
