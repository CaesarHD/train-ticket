package com.example.trainticket.repository;

import com.example.trainticket.model.Train;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrainRepository extends JpaRepository<Train, Long> {
    Optional<Train> findByTrainCode(String trainCode);
}
