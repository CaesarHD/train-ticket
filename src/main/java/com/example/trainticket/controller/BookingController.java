package com.example.trainticket.controller;

import com.example.trainticket.dto.ItineraryRequest;
import com.example.trainticket.dto.ItineraryResponse;
import com.example.trainticket.dto.RouteOption;
import com.example.trainticket.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ItineraryResponse bookTicket(@RequestBody ItineraryRequest request) {
        return bookingService.bookTicket(request);
    }

    @GetMapping("/all")
    public List<ItineraryResponse> getAllBookings() {
        return bookingService.getAllBookings();
    }

    @GetMapping("/routes")
    public List<RouteOption> findRoutes(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam LocalDate date) {
        return bookingService.findRoutes(from, to, date);
    }

    @GetMapping("/user/{id}")
    public List<ItineraryResponse> getAllUserBookings(@PathVariable Long id) {
        return bookingService.getAllUserBookings(id);
    }

    @PostMapping("/admin/delay/{trainCode}/{date}/{minutes}")
    @ResponseStatus(HttpStatus.OK)
    public void notifyDelay(
            @PathVariable String trainCode,
            @PathVariable String date,
            @PathVariable int minutes) {
        bookingService.notifyDelay(trainCode, LocalDate.parse(date), minutes);
    }
}
