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

import java.io.InputStream;
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
        try (InputStream is = new ClassPathResource("seed-data.yml").getInputStream()) {
            data = new Yaml().load(is);
        }

        List<String> stationNames = (List<String>) data.get("stations");
        List<List<String>> routeDefs = (List<List<String>>) data.get("routes");
        List<Map<String, Object>> trainDefs = (List<Map<String, Object>>) data.get("trains");
        List<Map<String, String>> userDefs = (List<Map<String, String>>) data.get("users");

        Map<String, Station> stationMap = new LinkedHashMap<>();
        for (Station station : stationRepository.findByNameIn(stationNames)) {
            stationMap.put(station.getName(), station);
        }
        for (String name : stationNames) {
            if (!stationMap.containsKey(name)) {
                stationMap.put(name, stationRepository.save(new Station(name)));
            }
        }

        List<Route> routeList = new ArrayList<>();
        for (List<String> names : routeDefs) {
            routeList.add(routeService.createRoute(names.stream().map(stationMap::get).toList()));
        }

        for (Map<String, Object> trainDef : trainDefs) {
            String code = (String) trainDef.get("code");
            if (trainRepository.findByTrainCode(code).isPresent()) {
                continue;
            }

            int capacity = (int) trainDef.get("capacity");
            int routeIndex = (int) trainDef.get("routeIndex");
            Map<String, String> arrivalsRaw = (Map<String, String>) trainDef.get("arrivals");
            Map<String, Integer> stopsRaw = (Map<String, Integer>) trainDef.get("stops");
            List<String> daysRaw = (List<String>) trainDef.get("operatingDays");

            Map<Station, LocalDateTime> arrivals = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : arrivalsRaw.entrySet()) {
                String[] parts = entry.getValue().split(":");
                arrivals.put(stationMap.get(entry.getKey()),
                        LocalDateTime.of(2025, 1, 1, Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            }
            Map<Station, Integer> stops = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : stopsRaw.entrySet()) {
                stops.put(stationMap.get(entry.getKey()), entry.getValue());
            }

            Train train = new Train(code, capacity, routeList.get(routeIndex));
            train.setArrivals(arrivals);
            train.setStopDurations(stops);
            if (daysRaw != null) {
                EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                for (String day : daysRaw) {
                    days.add(DayOfWeek.valueOf(day));
                }
                train.setOperatingDays(days);
            }
            trainRepository.save(train);
        }

        if (userDefs != null) {
            for (Map<String, String> userDef : userDefs) {
                String email = userDef.get("email");
                if (userRepository.findByEmail(email).isEmpty()) {
                    userRepository.save(new User(email, userDef.get("name")));
                }
            }
        }
    }
}
