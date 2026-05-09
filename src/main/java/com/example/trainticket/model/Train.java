package com.example.trainticket.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    @Getter
    @Setter
    @ElementCollection
    @CollectionTable(name = "train_schedules", joinColumns = @JoinColumn(name = "train_id"))
    @MapKeyJoinColumn(name = "station_id")
    @Column(name = "arrival_time")
    private Map<Station, LocalDateTime> schedule;

    @Getter
    @ElementCollection
    @CollectionTable(name = "route_seats", joinColumns = @JoinColumn(name = "train_id"))
    @MapKeyJoinColumn(name = "route_id")
    @Column(name = "route_seats")
    private Map<Route, Integer> routeSeats;

    @OneToMany(mappedBy = "train")
    private List<Booking> bookings;


    public Train(String trainCode, Integer capacity, Route route) {
        this.trainCode = trainCode;
        this.capacity = capacity;
        this.route = route;

        schedule = new HashMap<>();
        routeSeats = new HashMap<>();
    }

    public LocalDateTime getArrivalTimeFrom(Station station) {
        return schedule.get(station);
    }

    public void bookSeat(Route route) {
        routeSeats.merge(route, 1, Integer::sum);
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
