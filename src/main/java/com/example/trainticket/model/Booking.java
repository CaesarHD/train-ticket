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

    @Getter
    @ManyToOne
    @JoinColumn(name = "travel_id")
    private Travel travel;

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


    public Booking(Travel travel, Route route, User user) {
        this.travel = travel;
        this.route = route;
        this.travelDate = travel.getTravelDate();
        this.user = user;
    }

    public Train getTrain() {
        return travel.getTrain();
    }
}
