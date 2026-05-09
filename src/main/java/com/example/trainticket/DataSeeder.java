package com.example.trainticket;

import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;
import com.example.trainticket.model.Train;
import com.example.trainticket.model.User;
import com.example.trainticket.repository.StationRepository;
import com.example.trainticket.repository.TrainRepository;
import com.example.trainticket.repository.UserRepository;
import com.example.trainticket.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final StationRepository stationRepository;
    private final RouteService routeService;
    private final TrainRepository trainRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void run(String... args) throws Exception {
        Map<String, Object> data;
        try (var is = new ClassPathResource("seed-data.yml").getInputStream()) {
            data = new Yaml().load(is);
        }

        var stationNames = (List<String>) data.get("stations");
        var routeDefs = (List<List<String>>) data.get("routes");
        var trainDefs = (List<Map<String, Object>>) data.get("trains");
        var userDefs = (List<Map<String, String>>) data.get("users");

        Map<String, Station> stationMap = new LinkedHashMap<>();
        for (Station s : stationRepository.findByNameIn(stationNames)) {
            stationMap.put(s.getName(), s);
        }
        for (var name : stationNames) {
            if (!stationMap.containsKey(name)) {
                stationMap.put(name, stationRepository.save(new Station(name)));
            }
        }

        List<Route> routeList = new ArrayList<>();
        for (var names : routeDefs) {
            routeList.add(routeService.createRoute(names.stream().map(stationMap::get).toList()));
        }

        for (var td : trainDefs) {
            var code = (String) td.get("code");
            if (trainRepository.findByTrainCode(code).isPresent()) continue;

            var capacity = (int) td.get("capacity");
            var ri = (int) td.get("routeIndex");
            var arrivalsRaw = (Map<String, String>) td.get("arrivals");
            var stopsRaw = (Map<String, Integer>) td.get("stops");
            var daysRaw = (List<String>) td.get("operatingDays");

            Map<Station, LocalDateTime> arrivals = new LinkedHashMap<>();
            for (var e : arrivalsRaw.entrySet()) {
                var parts = e.getValue().split(":");
                arrivals.put(stationMap.get(e.getKey()),
                        LocalDateTime.of(2025, 1, 1, Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            }
            Map<Station, Integer> stops = new LinkedHashMap<>();
            for (var e : stopsRaw.entrySet()) {
                stops.put(stationMap.get(e.getKey()), e.getValue());
            }

            Train train = new Train(code, capacity, routeList.get(ri));
            train.setArrivals(arrivals);
            train.setStopDurations(stops);
            if (daysRaw != null) {
                var days = EnumSet.noneOf(DayOfWeek.class);
                for (var d : daysRaw) days.add(DayOfWeek.valueOf(d));
                train.setOperatingDays(days);
            }
            trainRepository.save(train);
        }

        if (userDefs != null) {
            for (var ud : userDefs) {
                var email = ud.get("email");
                if (userRepository.findByEmail(email).isEmpty()) {
                    userRepository.save(new User(email, ud.get("name")));
                }
            }
        }
    }
}
