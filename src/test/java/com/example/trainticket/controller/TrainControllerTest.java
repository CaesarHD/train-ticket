package com.example.trainticket.controller;

import com.example.trainticket.dto.ItineraryResponse;
import com.example.trainticket.dto.TrainResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
@Transactional
class TrainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAvailableTrains_ClujToBucuresti_returnsTRA001() throws Exception {
        var futureDate = LocalDate.now().plusDays(7);

        var result = mockMvc.perform(get("/api/trains/available")
                        .param("from", "Cluj-Napoca")
                        .param("to", "Bucuresti")
                        .param("date", futureDate.toString()))
                .andExpect(status().isOk())
                .andReturn();

        var trains = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TrainResponse>>() {});

        assertThat(trains).hasSize(1);
        var t = trains.get(0);
        assertThat(t.trainCode()).isEqualTo("TRA-001");
        assertThat(t.departureStation()).isEqualTo("Cluj-Napoca");
        assertThat(t.departureTime()).isEqualTo("06:00");
        assertThat(t.arrivalStation()).isEqualTo("Bucuresti");
        assertThat(t.arrivalTime()).isEqualTo("13:00");
        assertThat(t.routeDeparture()).isEqualTo("Cluj-Napoca");
        assertThat(t.routeArrival()).isEqualTo("Bucuresti");
    }

    @Test
    void getAvailableTrains_DejToSibiu_weekday_returnsTRA002() throws Exception {
        var date = LocalDate.now().plusDays(7);
        if (date.getDayOfWeek().name().matches("SATURDAY|SUNDAY")) {
            date = date.plusDays(2);
        }

        var result = mockMvc.perform(get("/api/trains/available")
                        .param("from", "Dej")
                        .param("to", "Sibiu")
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andReturn();

        var trains = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TrainResponse>>() {});

        assertThat(trains).hasSize(1);
        var t = trains.get(0);
        assertThat(t.trainCode()).isEqualTo("TRA-002");
        assertThat(t.departureStation()).isEqualTo("Dej");
        assertThat(t.departureTime()).isEqualTo("05:00");
        assertThat(t.arrivalStation()).isEqualTo("Sibiu");
        assertThat(t.arrivalTime()).isEqualTo("08:15");
        assertThat(t.routeDeparture()).isEqualTo("Dej");
        assertThat(t.routeArrival()).isEqualTo("Sibiu");
    }

    @Test
    void getAvailableTrains_unknownStation_returns404() throws Exception {
        mockMvc.perform(get("/api/trains/available")
                        .param("from", "Nowhere")
                        .param("to", "Bucuresti")
                        .param("date", LocalDate.now().plusDays(7).toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAvailableTrains_ClujToTurda_monday_returnsMultiple() throws Exception {
        var result = mockMvc.perform(get("/api/trains/available")
                        .param("from", "Cluj-Napoca")
                        .param("to", "Turda")
                        .param("date", "2026-05-18"))
                .andExpect(status().isOk())
                .andReturn();

        var trains = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TrainResponse>>() {});

        assertThat(trains).hasSizeGreaterThanOrEqualTo(2);
        assertThat(trains).extracting(TrainResponse::trainCode)
                .contains("TRA-001", "TRA-002", "TRA-003");
    }

    @Test
    void getAllBookings_noTravelForDate_returns404() throws Exception {
        var futureDate = LocalDate.now().plusDays(30);

        mockMvc.perform(get("/api/trains/bookings/TRA-001")
                        .param("date", futureDate.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllBookings_withExistingBooking_returnsItineraries() throws Exception {
        var futureDate = LocalDate.now().plusDays(14);
        if (futureDate.getDayOfWeek().name().matches("SATURDAY|SUNDAY")) {
            futureDate = futureDate.plusDays(2);
        }

        var body = """
                {
                    "segments": [
                        {
                            "trainCode": "TRA-002",
                            "departureStation": "Dej",
                            "arrivalStation": "Sibiu"
                        }
                    ],
                    "travelDate": "%s",
                    "userEmail": "test@example.com",
                    "userName": "Test User"
                }
                """.formatted(futureDate);

        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        var result = mockMvc.perform(get("/api/trains/bookings/TRA-002")
                        .param("date", futureDate.toString()))
                .andExpect(status().isOk())
                .andReturn();

        var itineraries = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<ItineraryResponse>>() {});

        assertThat(itineraries).isNotEmpty();
        var first = itineraries.get(0);
        assertThat(first.segments()).hasSize(1);
        assertThat(first.segments().get(0).trainCode()).isEqualTo("TRA-002");
        assertThat(first.segments().get(0).departureStation()).isEqualTo("Dej");
        assertThat(first.segments().get(0).arrivalStation()).isEqualTo("Sibiu");
    }
}
