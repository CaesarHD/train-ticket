package com.example.trainticket.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Table(name = "travels")
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@Entity
public class Travel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter
    @ManyToOne
    @JoinColumn(name = "train_id")
    private Train train;

    @Getter
    private LocalDate travelDate;

    @Getter
    @ElementCollection
    @CollectionTable(name = "travel_route_seats", joinColumns = @JoinColumn(name = "travel_id"))
    @MapKeyJoinColumn(name = "route_id")
    @Column(name = "remaining_seats")
    private Map<Route, Integer> routeSeats;

    @OneToMany(mappedBy = "travel")
    private List<Booking> bookings;

    public Travel(Train train, LocalDate travelDate, Map<Route, Integer> routeSeats) {
        this.train = train;
        this.travelDate = travelDate;
        this.routeSeats = new HashMap<>(routeSeats);
        this.bookings = new ArrayList<>();
    }
}
