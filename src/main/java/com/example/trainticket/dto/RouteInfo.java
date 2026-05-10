package com.example.trainticket.dto;

import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;

import java.util.List;

public record RouteInfo(
        Long id,
        List<String> stations
) {
    public static RouteInfo from(Route route) {
        return new RouteInfo(
                route.getId(),
                route.getStations().stream().map(Station::getName).toList()
        );
    }
}
