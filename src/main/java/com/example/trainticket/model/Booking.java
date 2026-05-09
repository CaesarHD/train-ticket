package com.example.trainticket.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @ManyToOne
    @JoinColumn(name = "train_id")
    @Getter
    private Train train;

    @ManyToOne
    @JoinColumn(name = "route_id")
    @Getter
    private Route route;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @Getter
    private User user;

    @Getter
    @Setter
    private LocalDateTime createdAt;

    @Getter
    @Setter
    private LocalDate travelDate;


    public Booking(Train train, Route route, LocalDate travelDate, User user) {
        this.train = train;
        this.route = route;
        this.travelDate = travelDate;
        this.user = user;
    }
}
