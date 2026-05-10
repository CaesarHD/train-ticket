package com.example.trainticket.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Table(name = "routes")
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@Entity
public class Route {
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Getter
    @ManyToMany
    @JoinTable(name = "route_stations",
        joinColumns = @JoinColumn(name = "route_id"),
        inverseJoinColumns = @JoinColumn(name = "station_id"))
    @OrderColumn(name = "station_order")
    private List<Station> stations;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Train> train;

    public Route(List<Station> stations, List<Train> train) {
        this.stations = stations;
        this.train = train;
    }

    public Station getDeparture() {
        return stations.getFirst();
    }

    public Station getArrival() {
        return stations.getLast();
    }

    public Integer getStationOrderNumber(Station station) {
        Integer index = stations.contains(station)
                ? stations.indexOf(station)
                : null;
        return stations.indexOf(station);
    }

}
