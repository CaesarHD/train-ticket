package com.example.trainticket.controller;

import com.example.trainticket.dto.BookingRequest;
import com.example.trainticket.dto.BookingResponse;
import com.example.trainticket.service.BookingService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public BookingResponse bookTicket(@RequestBody BookingRequest request) {
        return bookingService.bookTicket(request);
    }

    @GetMapping("/all")
    public List<BookingResponse> getAllBookings() {
        return bookingService.getAllBookings();
    }

}
