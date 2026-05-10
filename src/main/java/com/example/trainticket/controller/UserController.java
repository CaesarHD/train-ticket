package com.example.trainticket.controller;

import com.example.trainticket.dto.ItineraryResponse;
import com.example.trainticket.model.User;
import com.example.trainticket.repository.UserRepository;
import com.example.trainticket.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final BookingService bookingService;
    private final UserRepository userRepository;

    @GetMapping("/{id}")
    public List<ItineraryResponse> getAllUserBookings(@PathVariable Long id) {
        return bookingService.getAllUserBookings(id);
    }

    @GetMapping("/all")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

}
