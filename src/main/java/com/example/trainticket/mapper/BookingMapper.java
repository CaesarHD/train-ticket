package com.example.trainticket.mapper;

import com.example.trainticket.dto.BookingResponse;
import com.example.trainticket.model.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getTrain().getTrainCode(),
                booking.getRoute().getDeparture().getName(),
                booking.getRoute().getArrival().getName(),
                booking.getTravelDate(),
                booking.getCreatedAt(),
                booking.getUser().getName(),
                booking.getUser().getEmail()
        );
    }
}
