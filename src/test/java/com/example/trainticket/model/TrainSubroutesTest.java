package com.example.trainticket.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrainSubroutesTest {

    @Test
    void isSubroute_leftToRightSkipping_returnsTrue() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);
        Station stationC = new Station("C"); stationC.setId(3L);
        Station stationD = new Station("D"); stationD.setId(4L);

        Route trainRoute = new Route(List.of(stationA, stationB, stationC, stationD), null);
        Route subRoute = new Route(List.of(stationA, stationC, stationD), null);

        Train train = new Train("T1", 100, trainRoute);

        assertTrue(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_rightToLeft_returnsTrue() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);
        Station stationC = new Station("C"); stationC.setId(3L);
        Station stationD = new Station("D"); stationD.setId(4L);

        Route trainRoute = new Route(List.of(stationA, stationB, stationC, stationD), null);
        Route subRoute = new Route(List.of(stationD, stationB, stationA), null);

        Train train = new Train("T1", 100, trainRoute);

        assertTrue(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_wrongOrder_returnsFalse() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);
        Station stationC = new Station("C"); stationC.setId(3L);
        Station stationD = new Station("D"); stationD.setId(4L);

        Route trainRoute = new Route(List.of(stationA, stationB, stationC, stationD), null);
        Route subRoute = new Route(List.of(stationA, stationD, stationB), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_notMonotonic_returnsFalse() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);
        Station stationC = new Station("C"); stationC.setId(3L);

        Route trainRoute = new Route(List.of(stationA, stationB, stationC), null);
        Route subRoute = new Route(List.of(stationB, stationA, stationC), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_singleStation_returnsTrue() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);

        Route trainRoute = new Route(List.of(stationA, stationB), null);
        Route subRoute = new Route(List.of(stationB), null);

        Train train = new Train("T1", 100, trainRoute);

        assertTrue(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_longer_returnsFalse() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);
        Station stationC = new Station("C"); stationC.setId(3L);

        Route trainRoute = new Route(List.of(stationA, stationB), null);
        Route subRoute = new Route(List.of(stationA, stationB, stationC), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_empty_returnsFalse() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);

        Route trainRoute = new Route(List.of(stationA, stationB), null);
        Route subRoute = new Route(List.of(), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }

    @Test
    void isSubroute_stationNotInRoute_returnsFalse() {
        Station stationA = new Station("A"); stationA.setId(1L);
        Station stationB = new Station("B"); stationB.setId(2L);
        Station stationX = new Station("X"); stationX.setId(3L);

        Route trainRoute = new Route(List.of(stationA, stationB), null);
        Route subRoute = new Route(List.of(stationA, stationX), null);

        Train train = new Train("T1", 100, trainRoute);

        assertFalse(train.isSubroute(subRoute));
    }
}
