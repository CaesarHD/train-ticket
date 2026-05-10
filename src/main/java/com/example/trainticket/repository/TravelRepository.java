package com.example.trainticket.repository;

import com.example.trainticket.model.Train;
import com.example.trainticket.model.Travel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TravelRepository extends JpaRepository<Travel, Long> {

    @EntityGraph(attributePaths = {"train", "routeSeats"})
    Optional<Travel> findByTrainAndTravelDate(Train train, LocalDate travelDate);

    List<Travel> findByTrain(Train train);
}
