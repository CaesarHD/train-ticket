package com.example.trainticket.model;

import jakarta.persistence.*;
import lombok.*;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "train_id")
    private Train train;

    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    private String passengerName;

    public Booking(Train train, Route route, String passengerName) {
        this.train = train;
        this.route = route;
        this.passengerName = passengerName;
    }
}
