package com.example.trainticket.repository;

import com.example.trainticket.model.Route;
import com.example.trainticket.model.Train;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainRepository extends JpaRepository<Train, Long> {
    Optional<Train> findByTrainCode(String trainCode);

    List<Train> findByRoute(Route route);

    @EntityGraph(attributePaths = {"route.stations", "operatingDays"})
    List<Train> findAll();
}
