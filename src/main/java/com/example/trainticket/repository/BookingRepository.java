package com.example.trainticket.repository;

import com.example.trainticket.model.Booking;
import com.example.trainticket.model.Train;
import com.example.trainticket.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"travel", "userRoute", "user"})
    List<Booking> findByUser(User user);

    @EntityGraph(attributePaths = {"travel", "userRoute", "user"})
    List<Booking> findAll();
}
