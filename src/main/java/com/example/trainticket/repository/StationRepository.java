package com.example.trainticket.repository;

import com.example.trainticket.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {
    Optional<Station> findByName(String name);
    List<Station> findByNameIn(List<String> names);
}
