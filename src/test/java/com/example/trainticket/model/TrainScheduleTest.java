package com.example.trainticket.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrainScheduleTest {

    @Test
    void getArrivalTimeFrom_returnsCorrectTime() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);
        Station stationC = new Station("C"); stationC.setId(3L);

        Route route = new Route(List.of(stationA, stationB, stationC), null);

        LocalDateTime timeB = LocalDateTime.of(2025, 1, 1, 10, 0);
        Train train = new Train("T1", 100, route);
        Map<Station, LocalDateTime> arrivals = new HashMap<>();
        arrivals.put(stationA, LocalDateTime.of(2025, 1, 1, 9, 0));
        arrivals.put(stationB, timeB);
        arrivals.put(stationC, LocalDateTime.of(2025, 1, 1, 11, 0));
        train.setArrivals(arrivals);

        assertEquals(timeB, train.getArrivalTimeFrom(stationB));
    }

    @Test
    void getArrivalTimeFrom_unknownStation_returnsNull() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);
        Station unknown = new Station("X"); unknown.setId(3L);

        Route route = new Route(List.of(stationA, stationB), null);
        Train train = new Train("T1", 100, route);
        Map<Station, LocalDateTime> arrivals = new HashMap<>();
        arrivals.put(stationA, LocalDateTime.of(2025, 1, 1, 9, 0));
        arrivals.put(stationB, LocalDateTime.of(2025, 1, 1, 10, 0));
        train.setArrivals(arrivals);

        assertNull(train.getArrivalTimeFrom(unknown));
    }
}
