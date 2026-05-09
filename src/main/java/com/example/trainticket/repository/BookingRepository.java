package com.example.trainticket.repository;

import com.example.trainticket.model.Booking;
import com.example.trainticket.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"train", "route.stations", "user"})
    List<Booking> findByUser(User user);

    @EntityGraph(attributePaths = {"train", "route.stations", "user"})
    List<Booking> findAll();
}
