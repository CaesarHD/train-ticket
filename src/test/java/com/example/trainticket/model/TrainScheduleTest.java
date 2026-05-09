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
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);
        Station c = new Station("C"); c.setId(3L);

        Route route = new Route(List.of(a, b, c), null);

        LocalDateTime timeB = LocalDateTime.of(2025, 1, 1, 10, 0);
        Train train = new Train("T1", 100, route);
        Map<Station, LocalDateTime> arrivals = new HashMap<>();
        arrivals.put(a, LocalDateTime.of(2025, 1, 1, 9, 0));
        arrivals.put(b, timeB);
        arrivals.put(c, LocalDateTime.of(2025, 1, 1, 11, 0));
        train.setArrivals(arrivals);

        assertEquals(timeB, train.getArrivalTimeFrom(b));
    }

    @Test
    void getArrivalTimeFrom_unknownStation_returnsNull() {
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);
        Station unknown = new Station("X"); unknown.setId(3L);

        Route route = new Route(List.of(a, b), null);
        Train train = new Train("T1", 100, route);
        Map<Station, LocalDateTime> arrivals = new HashMap<>();
        arrivals.put(a, LocalDateTime.of(2025, 1, 1, 9, 0));
        arrivals.put(b, LocalDateTime.of(2025, 1, 1, 10, 0));
        train.setArrivals(arrivals);

        assertNull(train.getArrivalTimeFrom(unknown));
    }
}
