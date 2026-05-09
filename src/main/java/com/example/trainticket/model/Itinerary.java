package com.example.trainticket.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Table(name = "itineraries")
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@Entity
public class Itinerary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Getter
    private LocalDateTime createdAt;

    @Getter
    @OneToMany(mappedBy = "itinerary")
    private List<Booking> bookings;

    public Itinerary(User user) {
        this.user = user;
        this.createdAt = LocalDateTime.now();
        this.bookings = new ArrayList<>();
    }
}
