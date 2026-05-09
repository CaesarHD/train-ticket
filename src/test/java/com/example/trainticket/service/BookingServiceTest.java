package com.example.trainticket.service;

import com.example.trainticket.model.Booking;
import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;
import com.example.trainticket.model.Train;
import com.example.trainticket.repository.BookingRepository;
import com.example.trainticket.repository.RouteRepository;
import com.example.trainticket.repository.StationRepository;
import com.example.trainticket.repository.TrainRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    private Station nyc;
    private Station phl;
    private Station bal;
    private Station dc;
    private Station atl;
    private Station mia;

    @BeforeEach
    void setUp() {
        nyc = stationRepository.save(new Station("NYC"));
        phl = stationRepository.save(new Station("PHL"));
        bal = stationRepository.save(new Station("BAL"));
        dc = stationRepository.save(new Station("DC"));
        atl = stationRepository.save(new Station("ATL"));
        mia = stationRepository.save(new Station("MIA"));

        // Grand route: defines all possible contiguous station pairs
        routeService.createRoute(List.of(nyc, phl, bal, dc, atl));
        // Train is express: only stops at NYC→BAL→DC
        Route trainRoute = routeService.createRoute(List.of(nyc, bal, dc));
        trainRepository.save(new Train("T100", 100, trainRoute));
    }

    @Test
    void bookTicket_success() {
        Booking result = bookingService.bookTicket("T100", "NYC", "DC", "Alice");

        assertNotNull(result.getId());
        assertEquals("Alice", result.getPassengerName());

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
                () -> bookingService.bookTicket("WRONG", "NYC", "DC", "Alice"));
        assertEquals("Train not found: WRONG", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenDepartureStationNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "XXX", "DC", "Alice"));
        assertEquals("Station not found: XXX", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenArrivalStationNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "NYC", "YYY", "Alice"));
        assertEquals("Station not found: YYY", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenNoRouteBetweenStations() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "NYC", "MIA", "Alice"));
        assertEquals("No route from NYC to MIA", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenRouteNotSubroute() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "PHL", "ATL", "Alice"));
        assertEquals("Route is not a subroute of the train's route", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenNoAvailableSeats() {
        for (int i = 0; i < 100; i++) {
            bookingService.bookTicket("T100", "NYC", "DC", "Passenger" + i);
        }

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket("T100", "NYC", "DC", "Alice"));
        assertEquals("No available seats for this route", ex.getReason());
    }
}
