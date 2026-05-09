package com.example.trainticket.service;

import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;
import com.example.trainticket.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;

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
}
