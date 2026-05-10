package com.example.trainticket.mapper;

import com.example.trainticket.dto.ItineraryResponse;
import com.example.trainticket.dto.SegmentResponse;
import com.example.trainticket.model.Booking;
import com.example.trainticket.model.Itinerary;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class BookingMapper {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public ItineraryResponse toResponse(Itinerary itinerary) {
        List<Booking> bookings = itinerary.getBookings();
        List<SegmentResponse> segments = bookings.stream()
                .map(b -> new SegmentResponse(
                        b.getTrain().getTrainCode(),
                        b.getUserRoute().getDeparture().getName(),
                        b.getUserRoute().getArrival().getName(),
                        b.getTravel().getTrain().getDepartureTimeFrom(b.getUserRoute().getDeparture()).format(TIME_FMT),
                        b.getTravel().getTrain().getArrivalTimeFrom(b.getUserRoute().getArrival()).format(TIME_FMT)
                ))
                .toList();

        Booking first = bookings.getFirst();
        return new ItineraryResponse(
                itinerary.getId(),
                segments,
                first.getTravelDate(),
                first.getCreatedAt(),
                first.getUser().getName(),
                first.getUser().getEmail()
        );
    }
}
