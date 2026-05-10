package com.example.trainticket.service;

import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;
import com.example.trainticket.model.Train;
import com.example.trainticket.model.Travel;
import com.example.trainticket.repository.RouteRepository;
import com.example.trainticket.repository.TravelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TravelService {

    private final TravelRepository travelRepository;
    private final RouteRepository routeRepository;

    @Transactional
    public Travel findOrCreate(Train train, LocalDate date) {
        return travelRepository.findByTrainAndTravelDate(train, date)
                .orElseGet(() -> {
                    Map<Route, Integer> seats = new HashMap<>();
                    List<Route> allRoutes = routeRepository.findAllWithStations();
                    for (Route route : allRoutes) {
                        if (train.isSubroute(route)) {
                            seats.put(route, train.getCapacity());
                        }
                    }
                    return travelRepository.save(new Travel(train, date, seats));
                });
    }

    public void bookSeat(Travel travel, Route route) {
        Map<Route, Integer> seats = travel.getRouteSeats();
        if (seats.getOrDefault(route, 0) <= 0) {
            return;
        }

        List<Station> trainStations = travel.getTrain().getRoute().getStations();
        int depIdx = trainStations.indexOf(route.getDeparture());
        int arrIdx = trainStations.indexOf(route.getArrival());

        Set<Route> routes = Map.copyOf(seats).keySet();
        for (Route affectedRoute : routes) {
            int rDep = trainStations.indexOf(affectedRoute.getDeparture());
            int rArr = trainStations.indexOf(affectedRoute.getArrival());
            if (depIdx < rArr && rDep < arrIdx) {
                seats.merge(affectedRoute, -1, Integer::sum);
            }
        }
    }
}
