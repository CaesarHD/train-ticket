package com.example.trainticket.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrainSubroutesTest {

    @Test
    void isSubroute_leftToRightSkipping_returnsTrue() {
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);
        Station c = new Station("C"); c.setId(3L);
        Station d = new Station("D"); d.setId(4L);

        Route trainRoute = new Route(List.of(a, b, c, d), null);
        Route subRoute = new Route(List.of(a, c, d), null);

        Train train = new Train("T1", 100, trainRoute);

        assertTrue(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_rightToLeft_returnsTrue() {
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);
        Station c = new Station("C"); c.setId(3L);
        Station d = new Station("D"); d.setId(4L);

        Route trainRoute = new Route(List.of(a, b, c, d), null);
        Route subRoute = new Route(List.of(d, b, a), null);

        Train train = new Train("T1", 100, trainRoute);

        assertTrue(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_wrongOrder_returnsFalse() {
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);
        Station c = new Station("C"); c.setId(3L);
        Station d = new Station("D"); d.setId(4L);

        Route trainRoute = new Route(List.of(a, b, c, d), null);
        Route subRoute = new Route(List.of(a, d, b), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_notMonotonic_returnsFalse() {
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);
        Station c = new Station("C"); c.setId(3L);

        Route trainRoute = new Route(List.of(a, b, c), null);
        Route subRoute = new Route(List.of(b, a, c), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_singleStation_returnsTrue() {
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);

        Route trainRoute = new Route(List.of(a, b), null);
        Route subRoute = new Route(List.of(b), null);

        Train train = new Train("T1", 100, trainRoute);

        assertTrue(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_longer_returnsFalse() {
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);
        Station c = new Station("C"); c.setId(3L);

        Route trainRoute = new Route(List.of(a, b), null);
        Route subRoute = new Route(List.of(a, b, c), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_empty_returnsFalse() {
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);

        Route trainRoute = new Route(List.of(a, b), null);
        Route subRoute = new Route(List.of(), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_stationNotInRoute_returnsFalse() {
        Station a = new Station("A"); a.setId(1L);
        Station b = new Station("B"); b.setId(2L);
        Station x = new Station("X"); x.setId(3L);

        Route trainRoute = new Route(List.of(a, b), null);
        Route subRoute = new Route(List.of(a, x), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }
}
