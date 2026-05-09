package com.example.trainticket.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter
    @Setter
    @Column(nullable = false, unique = true, length = 100)
    String email;

    @Getter
    @Setter
    String name;

    @OneToMany(mappedBy = "user")
    private List<Booking> bookings;

    public User(String email, String name) {
        this.email = email;
        this.name = name;
    }
}
