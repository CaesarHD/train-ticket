package com.example.trainticket.repository;

import com.example.trainticket.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {

    @Query("select distinct r from Route r left join fetch r.stations")
    List<Route> findAllWithStations();
}
