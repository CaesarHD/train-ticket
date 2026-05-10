package com.example.trainticket.dto;

import java.util.List;

public record RouteRequest(
        List<String> stations
) {}
