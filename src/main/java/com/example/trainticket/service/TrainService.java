package com.example.trainticket.service;

import com.example.trainticket.dto.TrainRequest;
import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;
import com.example.trainticket.model.Train;
import com.example.trainticket.repository.StationRepository;
import com.example.trainticket.repository.TrainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainService {

    private final TrainRepository trainRepository;
    private final StationRepository stationRepository;
    private final RouteService routeService;

    public List<Train> findAll() {
        return trainRepository.findAll();
    }

    public Train findByCode(String trainCode) {
        return trainRepository.findByTrainCode(trainCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Train not found: " + trainCode));
    }

    @Transactional
    public Train create(TrainRequest request) {
        if (trainRepository.findByTrainCode(request.trainCode()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Train already exists: " + request.trainCode());
        }

        List<Station> stations = resolveStations(request.stations());
        Route route = routeService.createRoute(stations);

        Train train = new Train(request.trainCode(), request.capacity(), route);
        train.setArrivals(buildArrivals(request.arrivals(), stations));
        train.setStopDurations(buildStops(request.stopDurations(), stations));
        train.setOperatingDays(buildDays(request.operatingDays()));

        return trainRepository.save(train);
    }

    @Transactional
    public Train update(String trainCode, TrainRequest request) {
        Train train = findByCode(trainCode);

        if (!train.getTrainCode().equals(request.trainCode())
                && trainRepository.findByTrainCode(request.trainCode()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Train already exists: " + request.trainCode());
        }

        List<Station> stations = resolveStations(request.stations());
        Route route = routeService.createRoute(stations);

        train.setTrainCode(request.trainCode());
        train.setCapacity(request.capacity());
        train.setRoute(route);
        train.setArrivals(buildArrivals(request.arrivals(), stations));
        train.setStopDurations(buildStops(request.stopDurations(), stations));
        train.setOperatingDays(buildDays(request.operatingDays()));

        return trainRepository.save(train);
    }

    @Transactional
    public void delete(String trainCode) {
        Train train = findByCode(trainCode);
        trainRepository.delete(train);
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
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Station not found: " + name);
            }
            result.add(s);
        }
        return result;
    }

    private Map<Station, LocalDateTime> buildArrivals(Map<String, String> arrivals, List<Station> stations) {
        Set<String> allowed = stations.stream().map(Station::getName).collect(Collectors.toSet());
        for (String name : arrivals.keySet()) {
            if (!allowed.contains(name)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Station " + name + " is not on the route");
            }
        }

        Map<String, Station> byName = stations.stream().collect(Collectors.toMap(Station::getName, s -> s));
        Map<Station, LocalDateTime> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : arrivals.entrySet()) {
            String[] parts = entry.getValue().split(":");
            result.put(byName.get(entry.getKey()),
                    LocalDateTime.of(2025, 1, 1,
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
        }
        return result;
    }

    private Map<Station, Integer> buildStops(Map<String, Integer> stops, List<Station> stations) {
        Set<String> allowed = stations.stream().map(Station::getName).collect(Collectors.toSet());
        for (String name : stops.keySet()) {
            if (!allowed.contains(name)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Station " + name + " is not on the route");
            }
        }

        Map<String, Station> byName = stations.stream().collect(Collectors.toMap(Station::getName, s -> s));
        Map<Station, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : stops.entrySet()) {
            result.put(byName.get(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private Set<DayOfWeek> buildDays(List<String> days) {
        if (days == null) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String day : days) {
            result.add(DayOfWeek.valueOf(day.toUpperCase()));
        }
        return result;
    }
}
