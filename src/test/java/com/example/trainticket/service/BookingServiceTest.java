package com.example.trainticket.service;

import com.example.trainticket.model.*;
import com.example.trainticket.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
class BookingServiceTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RouteService routeService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private Station nyc;
    private Station phl;
    private Station bal;
    private Station dc;
    private Station atl;
    private Station mia;
    private User alice;

    @BeforeEach
    void setUp() {
        nyc = stationRepository.save(new Station("NYC"));
        phl = stationRepository.save(new Station("PHL"));
        bal = stationRepository.save(new Station("BAL"));
        dc = stationRepository.save(new Station("DC"));
        atl = stationRepository.save(new Station("ATL"));
        mia = stationRepository.save(new Station("MIA"));

        routeService.createRoute(List.of(nyc, phl, bal, dc, atl));
        Route trainRoute = routeService.createRoute(List.of(nyc, bal, dc));
        trainRepository.save(new Train("T100", 100, trainRoute));

        alice = userRepository.save(new User("alice@mail.com", "Alice"));
    }

    @Test
    void bookTicket_success() {
        Booking result = bookingService.bookTicket("T100", "NYC", "DC", LocalDate.now(), alice.getId());

        assertNotNull(result.getId());
        assertEquals("Alice", result.getUser().getName());

        entityManager.flush();
        entityManager.clear();

        Booking saved = bookingRepository.findById(result.getId()).orElseThrow();
        assertNotNull(saved);
        assertEquals("T100", saved.getTrain().getTrainCode());
        assertEquals("NYC", saved.getRoute().getStations().getFirst().getName());
        assertEquals("DC", saved.getRoute().getStations().getLast().getName());
    }

    @Test
    void bookTicket_throwsWhenTrainNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("WRONG", "NYC", "DC", LocalDate.now(), alice.getId()));
        assertEquals("Train not found: WRONG", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenDepartureStationNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "XXX", "DC", LocalDate.now(), alice.getId()));
        assertEquals("Station not found: XXX", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenArrivalStationNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "NYC", "YYY", LocalDate.now(), alice.getId()));
        assertEquals("Station not found: YYY", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenNoRouteBetweenStations() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "NYC", "MIA", LocalDate.now(), alice.getId()));
        assertEquals("No route from NYC to MIA", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenRouteNotSubroute() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "NYC", "PHL", LocalDate.now(), alice.getId()));
        assertEquals("Route is not a subroute of the train's route", ex.getReason());
    }


    @Test
    void bookTicket_throwsWhenNoAvailableSeats() {
        for (int i = 0; i < 100; i++) {
            User user = userRepository.save(new User(i + "@mail.com", "name" + i));
            bookingService.bookTicket("T100", "NYC", "DC", LocalDate.now(), user.getId());
        }

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "NYC", "DC", LocalDate.now(), alice.getId()));
        assertEquals("No available seats for this route", ex.getReason());
    }
}
