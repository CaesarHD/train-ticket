package com.example.trainticket.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;

@Table(name = "trains")
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@Entity
public class Train {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    private String trainCode;

    @Getter
    @Setter
    private Integer capacity;

    @Getter
    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    @Getter
    @Setter
    @ElementCollection
    @CollectionTable(name = "train_arrivals", joinColumns = @JoinColumn(name = "train_id"))
    @MapKeyJoinColumn(name = "station_id")
    @Column(name = "arrival_time")
    private Map<Station, LocalDateTime> arrivals;

    @Getter
    @Setter
    @ElementCollection
    @CollectionTable(name = "train_stop_durations", joinColumns = @JoinColumn(name = "train_id"))
    @MapKeyJoinColumn(name = "station_id")
    @Column(name = "stop_minutes")
    private Map<Station, Integer> stopDurations;

    @Getter
    @Setter
    @ElementCollection
    @CollectionTable(name = "train_operating_days", joinColumns = @JoinColumn(name = "train_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> operatingDays;

    public Train(String trainCode, Integer capacity, Route route) {
        this.trainCode = trainCode;
        this.capacity = capacity;
        this.route = route;

        arrivals = new HashMap<>();
        stopDurations = new HashMap<>();
        operatingDays = new HashSet<>();
    }

    public LocalDateTime getArrivalTimeFrom(Station station) {
        return arrivals.get(station);
    }

    public LocalDateTime getDepartureTimeFrom(Station station) {
        LocalDateTime arrival = arrivals.get(station);
        if (arrival == null) return null;
        Integer stop = stopDurations.getOrDefault(station, 0);
        return arrival.plusMinutes(stop);
    }

    public boolean isSubroute(Route route) {
        List<Station> trainStations = this.route.getStations();
        List<Station> subStations = route.getStations();

        if (subStations.isEmpty() || subStations.size() > trainStations.size()) {
            return false;
        }

        List<Integer> indices = new ArrayList<>();
        for (Station s : subStations) {
            int idx = trainStations.indexOf(s);
            if (idx == -1) return false;
            indices.add(idx);
        }

        boolean increasing = true;
        boolean decreasing = true;
        for (int i = 1; i < indices.size(); i++) {
            if (indices.get(i) <= indices.get(i - 1)) increasing = false;
            if (indices.get(i) >= indices.get(i - 1)) decreasing = false;
        }

        return increasing || decreasing;
    }

}
