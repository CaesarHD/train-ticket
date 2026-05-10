package com.example.trainticket.dto;

import com.example.trainticket.model.Train;

import java.util.List;

public record TrainInfo(
        Long id,
        String trainCode,
        Integer capacity,
        Long routeId,
        List<String> operatingDays
) {
    public static TrainInfo from(Train train) {
        return new TrainInfo(
                train.getId(),
                train.getTrainCode(),
                train.getCapacity(),
                train.getRoute().getId(),
                train.getOperatingDays() == null ? List.of()
                        : train.getOperatingDays().stream().map(Enum::name).toList()
        );
    }
}
