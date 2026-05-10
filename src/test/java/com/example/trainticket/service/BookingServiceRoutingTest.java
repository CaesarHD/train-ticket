package com.example.trainticket.service;

import com.example.trainticket.dto.RouteOption;
import com.example.trainticket.dto.SegmentOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
class BookingServiceRoutingTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 5, 11);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 5, 12);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 5, 10);

    @Autowired
    private BookingService bookingService;

    @Test
    void directRoute_returnsSingleSegmentZeroTransfers() {
        List<RouteOption> routes = bookingService.findRoutes("Cluj-Napoca", "Bucuresti", MONDAY);

        assertThat(routes).isNotEmpty();
        RouteOption best = routes.get(0);
        assertThat(best.transfers()).isZero();
        assertThat(best.segments()).hasSize(1);
        assertThat(best.segments().get(0).trainCode()).isEqualTo("TRA-001");
        assertThat(best.segments().get(0).departureStation()).isEqualTo("Cluj-Napoca");
        assertThat(best.segments().get(0).arrivalStation()).isEqualTo("Bucuresti");
        assertThat(best.totalMinutes()).isPositive();
        assertThat(best.minRemainingSeats()).isPositive();
    }

    @Test
    void oneTransfer_returnsTwoSegments() {
        List<RouteOption> routes = bookingService.findRoutes("Timisoara", "Bucuresti", MONDAY);

        assertThat(routes).isNotEmpty();
        RouteOption best = routes.get(0);
        assertThat(best.transfers()).isEqualTo(1);
        assertThat(best.segments()).hasSize(2);

        SegmentOption first = best.segments().get(0);
        assertThat(first.trainCode()).isEqualTo("TRA-005");
        assertThat(first.departureStation()).isEqualTo("Timisoara");
        assertThat(first.arrivalStation()).isEqualTo("Sibiu");

        SegmentOption second = best.segments().get(1);
        assertThat(second.trainCode()).isEqualTo("TRA-006");
        assertThat(second.departureStation()).isEqualTo("Sibiu");
        assertThat(second.arrivalStation()).isEqualTo("Bucuresti");
    }

    @Test
    void twoTransfers_returnsThreeSegments() {
        List<RouteOption> routes = bookingService.findRoutes("Timisoara", "Galati", MONDAY);

        assertThat(routes).isNotEmpty();
        RouteOption best = routes.get(0);

        assertThat(best.transfers()).isEqualTo(2);
        assertThat(best.segments()).hasSize(3);

        assertThat(best.segments().get(0).trainCode()).isEqualTo("TRA-005");
        assertThat(best.segments().get(1).trainCode()).isEqualTo("TRA-006");
        assertThat(best.segments().get(2).trainCode()).matches("TRA-007|TRA-008");

        assertSegmentsConnect(best.segments());
    }

    @Test
    void vienaToBucuresti_findsRoute() {
        List<RouteOption> routes = bookingService.findRoutes("Viena", "Bucuresti", MONDAY);

        assertThat(routes).isNotEmpty();
        RouteOption best = routes.get(0);

        assertThat(best.transfers()).isEqualTo(1);
        assertThat(best.segments()).hasSize(2);
        assertThat(best.segments().get(0).trainCode()).isEqualTo("TRA-005");
        assertThat(best.segments().get(1).trainCode()).isEqualTo("TRA-006");
        assertSegmentsConnect(best.segments());
    }

    @Test
    void dejToBucuresti_hasTwoOptions() {
        List<RouteOption> routes = bookingService.findRoutes("Dej", "Bucuresti", MONDAY);

        assertThat(routes).isNotEmpty();
        assertThat(routes.get(0).transfers()).isEqualTo(1);

        assertThat(routes.get(0).segments().get(0).trainCode()).isEqualTo("TRA-002");
        assertThat(routes.get(0).segments().get(1).trainCode()).isIn("TRA-001", "TRA-006");

        assertSegmentsConnect(routes.get(0).segments());
    }

    @Test
    void results_sortedByTotalMinutes() {
        List<RouteOption> routes = bookingService.findRoutes("Cluj-Napoca", "Galati", MONDAY);

        assertThat(routes).isNotEmpty();
        for (int i = 1; i < routes.size(); i++) {
            assertThat(routes.get(i).totalMinutes())
                    .isGreaterThanOrEqualTo(routes.get(i - 1).totalMinutes());
        }
    }

    @Test
    void noOperatingTrains_returnsEmpty() {
        List<RouteOption> routes = bookingService.findRoutes("Oradea", "Sighisoara", TUESDAY);

        assertThat(routes).isEmpty();
    }

    @Test
    void unknownStation_throwsException() {
        assertThrows(ResponseStatusException.class,
                () -> bookingService.findRoutes("Nowhere", "Bucuresti", MONDAY));
        assertThrows(ResponseStatusException.class,
                () -> bookingService.findRoutes("Cluj-Napoca", "Nowhere", MONDAY));
    }

    @Test
    void clujToBrasov_singleTrain() {
        List<RouteOption> routes = bookingService.findRoutes("Cluj-Napoca", "Brasov", MONDAY);

        assertThat(routes).isNotEmpty();
        assertThat(routes.get(0).transfers()).isZero();
        assertThat(routes.get(0).segments().get(0).trainCode()).isEqualTo("TRA-001");
    }

    @Test
    void sibiuToBucuresti_directTrain() {
        List<RouteOption> routes = bookingService.findRoutes("Sibiu", "Bucuresti", MONDAY);

        assertThat(routes).isNotEmpty();
        assertThat(routes.get(0).transfers()).isZero();
        assertThat(routes.get(0).segments().get(0).trainCode()).isEqualTo("TRA-006");
    }

    @Test
    void sameStation_returnsEmpty() {
        List<RouteOption> routes = bookingService.findRoutes("Cluj-Napoca", "Cluj-Napoca", MONDAY);

        assertThat(routes).isEmpty();
    }

    @Test
    void weekend_returnsRoutesForDailyTrains() {
        List<RouteOption> weekday = bookingService.findRoutes("Cluj-Napoca", "Bucuresti", MONDAY);
        assertThat(weekday).isNotEmpty();

        List<RouteOption> weekend = bookingService.findRoutes("Cluj-Napoca", "Bucuresti", SUNDAY);
        assertThat(weekend).isNotEmpty();
    }

    private void assertSegmentsConnect(List<SegmentOption> segments) {
        for (int i = 1; i < segments.size(); i++) {
            assertThat(segments.get(i).departureStation())
                    .as("segment %d departure should match segment %d arrival", i, i - 1)
                    .isEqualTo(segments.get(i - 1).arrivalStation());
        }
    }
}
