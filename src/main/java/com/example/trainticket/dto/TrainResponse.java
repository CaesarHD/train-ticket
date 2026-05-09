package com.example.trainticket.dto;

import com.example.trainticket.model.Route;
import com.example.trainticket.model.Train;
import com.example.trainticket.model.Travel;

import java.time.format.DateTimeFormatter;

public record TrainResponse(
        String trainCode,
        int remainingSeats,
        String departureStation,
        String departureTime,
        String destinationStation,
        String arrivalTime,
        String routeDeparture,
        String routeDepartureTime,
        String routeDestination,
        String routeArrivalTime
) {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static TrainResponse from(Train train, Travel travel, Route route) {
        return new TrainResponse(
                train.getTrainCode(),
                travel.getRouteSeats().getOrDefault(route, train.getCapacity()),
                route.getDeparture().getName(),
                train.getDepartureTimeFrom(route.getDeparture()).format(TIME_FMT),
                route.getArrival().getName(),
                train.getArrivalTimeFrom(route.getArrival()).format(TIME_FMT),
                train.getRoute().getDeparture().getName(),
                train.getDepartureTimeFrom(train.getRoute().getDeparture()).format(TIME_FMT),
                train.getRoute().getArrival().getName(),
                train.getArrivalTimeFrom(train.getRoute().getArrival()).format(TIME_FMT)
        );
    }
}
