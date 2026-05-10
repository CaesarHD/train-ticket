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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private static final String NEW_TRAIN_CODE = "TRA-TEST-001";
    private static final List<String> ALL_DAYS = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");


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

    @Test
    void createTrain_appearsInAvailableTrains() throws Exception {
        var date = LocalDate.now().plusDays(7);

        var createBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(post("/api/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        var result = mockMvc.perform(get("/api/trains/available")
                        .param("from", "Cluj-Napoca")
                        .param("to", "Medias")
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andReturn();

        var trains = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TrainResponse>>() {});

        assertThat(trains).anyMatch(t -> t.trainCode().equals(NEW_TRAIN_CODE));
    }

    @Test
    void deleteTrain_bookingFails() throws Exception {
        var date = LocalDate.now().plusDays(7);

        var createBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(post("/api/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/trains/" + NEW_TRAIN_CODE))
                .andExpect(status().isNoContent());

        var bookingBody = """
                {
                    "segments": [{"trainCode": "%s", "departureStation": "Cluj-Napoca", "arrivalStation": "Medias"}],
                    "travelDate": "%s",
                    "userEmail": "test@example.com",
                    "userName": "Test User"
                }
                """.formatted(NEW_TRAIN_CODE, date);

        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void modifyTrainRoute_oldRouteBookingFails() throws Exception {
        var date = LocalDate.now().plusDays(7);

        var createBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(post("/api/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        var updateBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Dej", "Gherla", "Cluj-Napoca"],
                    "arrivals": {"Dej": "05:00", "Gherla": "05:25", "Cluj-Napoca": "06:00"},
                    "stopDurations": {"Dej": 0, "Gherla": 2, "Cluj-Napoca": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(put("/api/trains/" + NEW_TRAIN_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        var oldBookingBody = """
                {
                    "segments": [{"trainCode": "%s", "departureStation": "Cluj-Napoca", "arrivalStation": "Medias"}],
                    "travelDate": "%s",
                    "userEmail": "test@example.com",
                    "userName": "Test User"
                }
                """.formatted(NEW_TRAIN_CODE, date);

        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oldBookingBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modifyTrainRoute_newRouteBookingSucceeds() throws Exception {
        var date = LocalDate.now().plusDays(7);

        var createBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(post("/api/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        var updateBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Dej", "Gherla", "Cluj-Napoca"],
                    "arrivals": {"Dej": "05:00", "Gherla": "05:25", "Cluj-Napoca": "06:00"},
                    "stopDurations": {"Dej": 0, "Gherla": 2, "Cluj-Napoca": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(put("/api/trains/" + NEW_TRAIN_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        var newBookingBody = """
                {
                    "segments": [{"trainCode": "%s", "departureStation": "Dej", "arrivalStation": "Cluj-Napoca"}],
                    "travelDate": "%s",
                    "userEmail": "test@example.com",
                    "userName": "Test User"
                }
                """.formatted(NEW_TRAIN_CODE, date);

        var result = mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newBookingBody))
                .andExpect(status().isCreated())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ItineraryResponse.class);

        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).trainCode()).isEqualTo(NEW_TRAIN_CODE);
        assertThat(response.segments().get(0).departureStation()).isEqualTo("Dej");
        assertThat(response.segments().get(0).arrivalStation()).isEqualTo("Cluj-Napoca");
    }

    @Test
    void createTrain_unknownStation_returns400() throws Exception {
        var body = """
                {
                    "trainCode": "TRA-BAD",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Atlantis"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Atlantis": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Atlantis": 0},
                    "operatingDays": ["MONDAY"]
                }
                """;

        mockMvc.perform(post("/api/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTrain_duplicateCode_returns409() throws Exception {
        var body = """
                {
                    "trainCode": "TRA-001",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": ["MONDAY"]
                }
                """;

        mockMvc.perform(post("/api/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteTrain_unknownCode_returns404() throws Exception {
        mockMvc.perform(delete("/api/trains/TRA-NONEXISTENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTrain_unknownCode_returns404() throws Exception {
        var body = """
                {
                    "trainCode": "TRA-NONEXISTENT",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 0},
                    "operatingDays": ["MONDAY"]
                }
                """;

        mockMvc.perform(put("/api/trains/TRA-NONEXISTENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

}
