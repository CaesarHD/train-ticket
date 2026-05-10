package com.example.trainticket.service;

import com.example.trainticket.dto.RouteRequest;
import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;
import com.example.trainticket.model.Train;
import com.example.trainticket.model.Travel;
import com.example.trainticket.repository.RouteRepository;
import com.example.trainticket.repository.StationRepository;
import com.example.trainticket.repository.TrainRepository;
import com.example.trainticket.repository.TravelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final TravelRepository travelRepository;

    public List<Route> findAll() {
        return routeRepository.findAllWithStations();
    }

    public Route findById(Long id) {
        return routeRepository.findAllWithStations().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Route not found: " + id));
    }

    @Transactional
    public Route create(RouteRequest request) {
        List<Station> stations = resolveStations(request.stations());
        return createRoute(stations);
    }

    @Transactional
    public Route update(Long id, RouteRequest request) {
        Route route = findById(id);

        List<Train> trains = trainRepository.findByRoute(route);
        if (!trains.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot modify route with " + trains.size() + " train(s) assigned");
        }

        List<Station> stations = resolveStations(request.stations());
        Set<Long> newIds = stations.stream().map(Station::getId).collect(Collectors.toSet());
        Set<Long> oldIds = route.getStations().stream().map(Station::getId).collect(Collectors.toSet());
        if (newIds.equals(oldIds)) {
            return route;
        }

        routeRepository.delete(route);
        return createRoute(stations);
    }

    @Transactional
    public void delete(Long id) {
        Route route = findById(id);

        List<Train> trains = trainRepository.findByRoute(route);
        for (Train train : trains) {
            List<Travel> travels = travelRepository.findByTrain(train);
            travelRepository.deleteAll(travels);
            trainRepository.delete(train);
        }

        routeRepository.delete(route);
    }

    @Transactional
    public Route createRoute(List<Station> stations) {
        List<Route> allRoutes = routeRepository.findAllWithStations();
        Route existing = findMatching(allRoutes, stations);
        if (existing != null) return existing;

        Route route = routeRepository.save(new Route(stations, null));
        allRoutes.add(route);

        for (int i = 0; i < stations.size(); i++) {
            for (int j = i + 1; j < stations.size(); j++) {
                if (i == 0 && j == stations.size() - 1) continue;
                List<Station> sub = stations.subList(i, j + 1);
                if (findMatching(allRoutes, sub) != null) continue;
                Route subRoute = routeRepository.save(new Route(new ArrayList<>(sub), null));
                allRoutes.add(subRoute);
            }
        }
        return route;
    }

    private Route findMatching(List<Route> routes, List<Station> stations) {
        List<Long> ids = stations.stream().map(Station::getId).toList();
        for (Route r : routes) {
            List<Station> rs = r.getStations();
            if (rs.size() != ids.size()) continue;
            boolean match = true;
            for (int i = 0; i < ids.size(); i++) {
                if (!rs.get(i).getId().equals(ids.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) return r;
        }
        return null;
    }

    private List<Station> resolveStations(List<String> stationNames) {
        if (stationNames == null || stationNames.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least 2 stations are required");
        }
        Set<String> unique = new LinkedHashSet<>(stationNames);
        if (unique.size() != stationNames.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Duplicate stations in route");
        }

        Map<String, Station> byName = stationRepository.findByNameIn(stationNames)
                .stream().collect(Collectors.toMap(Station::getName, s -> s));

        List<Station> result = new ArrayList<>();
        for (String name : stationNames) {
            Station s = byName.get(name);
            if (s == null) {
                s = stationRepository.save(new Station(name));
                byName.put(name, s);
            }
            result.add(s);
        }
        return result;
    }
}
