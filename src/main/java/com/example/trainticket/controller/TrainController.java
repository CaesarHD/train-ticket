package com.example.trainticket.controller;

import com.example.trainticket.dto.ItineraryResponse;
import com.example.trainticket.dto.TrainInfo;
import com.example.trainticket.dto.TrainRequest;
import com.example.trainticket.dto.TrainResponse;
import com.example.trainticket.service.BookingService;
import com.example.trainticket.service.TrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trains")
@RequiredArgsConstructor
public class TrainController {

    private final BookingService bookingService;
    private final TrainService trainService;

    @GetMapping("/available")
    public List<TrainResponse> getAvailableTrains(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.getAvailableTrains(from, to, date);
    }

    @GetMapping("/bookings/{trainCode}")
    public List<ItineraryResponse> getAllBookings(
            @PathVariable String trainCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.getAllTrainBookings(trainCode, date);
    }

    @GetMapping
    public List<TrainInfo> getAll() {
        return trainService.findAll().stream()
                .map(TrainInfo::from)
                .toList();
    }

    @GetMapping("/{trainCode}")
    public TrainInfo getByCode(@PathVariable String trainCode) {
        return TrainInfo.from(trainService.findByCode(trainCode));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainInfo create(@Valid @RequestBody TrainRequest request) {
        return TrainInfo.from(trainService.create(request));
    }

    @PutMapping("/{trainCode}")
    public TrainInfo update(@PathVariable String trainCode, @Valid @RequestBody TrainRequest request) {
        return TrainInfo.from(trainService.update(trainCode, request));
    }

    @DeleteMapping("/{trainCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String trainCode) {
        trainService.delete(trainCode);
    }
}
