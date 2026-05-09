package com.example.trainticket.service;

import com.example.trainticket.dto.BookingRequest;
import com.example.trainticket.dto.BookingResponse;
import com.example.trainticket.dto.BookingSegment;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private TrainRepository trainRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RouteService routeService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItineraryRepository itineraryRepository;

    private Station cluj;
    private Station turda;
    private Station medias;
    private Station sighisoara;
    private Station brasov;
    private Station dej;
    private User alice;

    @BeforeEach
    void setUp() {
        cluj = stationRepository.findByName("Cluj-Napoca").orElseThrow();
        turda = stationRepository.findByName("Turda").orElseThrow();
        medias = stationRepository.findByName("Medias").orElseThrow();
        sighisoara = stationRepository.findByName("Sighisoara").orElseThrow();
        brasov = stationRepository.findByName("Brasov").orElseThrow();
        dej = stationRepository.findByName("Dej").orElseThrow();

        routeService.createRoute(List.of(dej, cluj, turda, medias, sighisoara, brasov));
        Route trainRoute = routeService.createRoute(List.of(dej, medias, sighisoara));
        var train = new Train("T100", 100, trainRoute);
        train.setOperatingDays(EnumSet.allOf(DayOfWeek.class));
        Map<Station, LocalDateTime> arrivals = new LinkedHashMap<>();
        arrivals.put(dej, LocalDateTime.of(2025, 1, 1, 5, 0));
        arrivals.put(medias, LocalDateTime.of(2025, 1, 1, 6, 0));
        arrivals.put(sighisoara, LocalDateTime.of(2025, 1, 1, 7, 0));
        train.setArrivals(arrivals);
        Map<Station, Integer> stops = new LinkedHashMap<>();
        stops.put(dej, 0);
        stops.put(medias, 5);
        stops.put(sighisoara, 0);
        train.setStopDurations(stops);
        trainRepository.save(train);

        alice = userRepository.save(new User("alice@mail.com", "Alice"));
    }

    private BookingRequest req(String trainCode, String dep, String dest, LocalDate date, String email) {
        return new BookingRequest(
                List.of(new BookingSegment(trainCode, dep, dest)),
                date, email, email);
    }

    @Test
    void bookTicket_success() {
        BookingResponse result = bookingService.bookTicket(
                req("T100", "Dej", "Sighisoara", LocalDate.now(), "alice@mail.com"));

        assertNotNull(result.id());
        assertEquals("Alice", result.userName());
        assertEquals(1, result.segments().size());
        assertEquals("T100", result.segments().getFirst().trainCode());
        assertEquals("Dej", result.segments().getFirst().departureStation());
        assertEquals("Sighisoara", result.segments().getFirst().destinationStation());

        entityManager.flush();
        entityManager.clear();

        var savedItinerary = itineraryRepository.findById(result.id()).orElseThrow();
        Booking saved = savedItinerary.getBookings().getFirst();
        assertNotNull(saved);
        assertEquals("T100", saved.getTrain().getTrainCode());
        assertEquals("Dej", saved.getUserRoute().getStations().getFirst().getName());
        assertEquals("Sighisoara", saved.getUserRoute().getStations().getLast().getName());
    }

    @Test
    void bookTicket_throwsWhenTrainNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket(
                        req("WRONG", "Dej", "Sighisoara", LocalDate.now(), "alice@mail.com")));
        assertEquals("Train not found: WRONG", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenDepartureStationNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket(
                        req("T100", "XXX", "Sighisoara", LocalDate.now(), "alice@mail.com")));
        assertEquals("Station not found: XXX", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenArrivalStationNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket(
                        req("T100", "Dej", "YYY", LocalDate.now(), "alice@mail.com")));
        assertEquals("Station not found: YYY", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenNoRouteBetweenStations() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket(
                        req("T100", "Satu Mare", "Sinaia", LocalDate.now(), "alice@mail.com")));
        assertEquals("No route from Satu Mare to Sinaia", ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenRouteNotSubroute() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket(
                        req("T100", "Dej", "Cluj-Napoca", LocalDate.now(), "alice@mail.com")));
        assertEquals("Route is not a subroute of the train's route", ex.getReason());
    }


    @Test
    void bookTicket_throwsWhenNoAvailableSeats() {
        for (int i = 0; i < 100; i++) {
            bookingService.bookTicket(
                    req("T100", "Medias", "Sighisoara", LocalDate.now(), i + "@mail.com"));
        }

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket(
                        req("T100", "Medias", "Sighisoara", LocalDate.now(), "full@mail.com")));
        assertEquals("No available seats for this route", ex.getReason());
    }


    @Test
    void bookTicket_throwsWhenDateInPast() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket(
                        req("T100", "Dej", "Sighisoara", yesterday, "alice@mail.com")));
        assertEquals("Not a valid date" + yesterday, ex.getReason());
    }

    @Test
    void bookTicket_throwsWhenDateMoreThanOneYearAhead() {
        LocalDate farFuture = LocalDate.now().plusYears(2);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket(
                        req("T100", "Dej", "Sighisoara", farFuture, "alice@mail.com")));
        assertEquals("Not a valid date" + farFuture, ex.getReason());
    }

    @Test
    void bookTicket_succeedsWhenOperatingDay() {
        var a = stationRepository.save(new Station("DepA"));
        var b = stationRepository.save(new Station("ArrB"));
        routeService.createRoute(List.of(a, b));
        var train = new Train("T-OPDAY", 50, routeService.createRoute(List.of(a, b)));
        train.setOperatingDays(EnumSet.of(DayOfWeek.MONDAY));
        Map<Station, LocalDateTime> arrivals = new LinkedHashMap<>();
        arrivals.put(a, LocalDateTime.of(2025, 1, 1, 10, 0));
        arrivals.put(b, LocalDateTime.of(2025, 1, 1, 11, 0));
        train.setArrivals(arrivals);
        Map<Station, Integer> stops = new LinkedHashMap<>();
        stops.put(a, 0);
        stops.put(b, 0);
        train.setStopDurations(stops);
        trainRepository.save(train);

        BookingResponse result = bookingService.bookTicket(
                req("T-OPDAY", "DepA", "ArrB", LocalDate.of(2026, 5, 11), "opday@mail.com"));

        assertNotNull(result.id());
        assertEquals("opday@mail.com", result.userName());
    }

    @Test
    void bookTicket_throwsWhenNotOperatingDay() {
        var a = stationRepository.save(new Station("DepC"));
        var b = stationRepository.save(new Station("ArrD"));
        routeService.createRoute(List.of(a, b));
        var train = new Train("T-NOPDAY", 50, routeService.createRoute(List.of(a, b)));
        train.setOperatingDays(EnumSet.of(DayOfWeek.MONDAY));
        trainRepository.save(train);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.bookTicket(
                        req("T-NOPDAY", "DepC", "ArrD", LocalDate.of(2026, 5, 12), "nopday@mail.com")));
        assertEquals("Train T-NOPDAY does not operate on TUESDAY", ex.getReason());
    }
}
