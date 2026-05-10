package com.example.trainticket.controller;

import com.example.trainticket.dto.RouteInfo;
import com.example.trainticket.dto.RouteRequest;
import com.example.trainticket.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping
    public List<RouteInfo> getAll() {
        return routeService.findAll().stream()
                .map(RouteInfo::from)
                .toList();
    }

    @GetMapping("/{id}")
    public RouteInfo getById(@PathVariable Long id) {
        return RouteInfo.from(routeService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RouteInfo create(@RequestBody RouteRequest request) {
        return RouteInfo.from(routeService.create(request));
    }

    @PutMapping("/{id}")
    public RouteInfo update(@PathVariable Long id, @RequestBody RouteRequest request) {
        return RouteInfo.from(routeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        routeService.delete(id);
    }
}
