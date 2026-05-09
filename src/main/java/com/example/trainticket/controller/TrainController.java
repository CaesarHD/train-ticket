package com.example.trainticket.controller;

import com.example.trainticket.dto.TrainResponse;
import com.example.trainticket.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trains")
@RequiredArgsConstructor
public class TrainController {

    private final BookingService bookingService;

    @GetMapping("/available")
    public List<TrainResponse> getAvailableTrains(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.getAvailableTrains(from, to, date);
    }
}
