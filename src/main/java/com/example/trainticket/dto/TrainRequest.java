package com.example.trainticket.dto;

import java.util.List;
import java.util.Map;

public record TrainRequest(
        String trainCode,
        Integer capacity,
        List<String> stations,
        Map<String, String> arrivals,
        Map<String, Integer> stopDurations,
        List<String> operatingDays
) {}
