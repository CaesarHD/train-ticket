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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
        LocalDate futureDate = LocalDate.now().plusDays(7);

        MvcResult result = mockMvc.perform(get("/api/trains/available")
                        .param("from", "Cluj-Napoca")
                        .param("to", "Bucuresti")
                        .param("date", futureDate.toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<TrainResponse> trains = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TrainResponse>>() {});

        assertThat(trains).hasSize(1);
        TrainResponse firstTrain = trains.get(0);
        assertThat(firstTrain.trainCode()).isEqualTo("TRA-001");
        assertThat(firstTrain.departureStation()).isEqualTo("Cluj-Napoca");
        assertThat(firstTrain.departureTime()).isEqualTo("06:00");
        assertThat(firstTrain.arrivalStation()).isEqualTo("Bucuresti");
        assertThat(firstTrain.arrivalTime()).isEqualTo("13:00");
        assertThat(firstTrain.routeDeparture()).isEqualTo("Cluj-Napoca");
        assertThat(firstTrain.routeArrival()).isEqualTo("Bucuresti");
    }

    @Test
    void getAvailableTrains_DejToSibiu_weekday_returnsTRA002() throws Exception {
        LocalDate date = LocalDate.now().plusDays(7);
        if (date.getDayOfWeek().name().matches("SATURDAY|SUNDAY")) {
            date = date.plusDays(2);
        }

        MvcResult result = mockMvc.perform(get("/api/trains/available")
                        .param("from", "Dej")
                        .param("to", "Sibiu")
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<TrainResponse> trains = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TrainResponse>>() {});

        assertThat(trains).hasSize(1);
        TrainResponse firstTrain = trains.get(0);
        assertThat(firstTrain.trainCode()).isEqualTo("TRA-002");
        assertThat(firstTrain.departureStation()).isEqualTo("Dej");
        assertThat(firstTrain.departureTime()).isEqualTo("05:00");
        assertThat(firstTrain.arrivalStation()).isEqualTo("Sibiu");
        assertThat(firstTrain.arrivalTime()).isEqualTo("08:15");
        assertThat(firstTrain.routeDeparture()).isEqualTo("Dej");
        assertThat(firstTrain.routeArrival()).isEqualTo("Sibiu");
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
        LocalDate monday = LocalDate.now().plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        MvcResult result = mockMvc.perform(get("/api/trains/available")
                        .param("from", "Cluj-Napoca")
                        .param("to", "Turda")
                        .param("date", monday.toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<TrainResponse> trains = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TrainResponse>>() {});

        assertThat(trains).hasSizeGreaterThanOrEqualTo(2);
        assertThat(trains).extracting(TrainResponse::trainCode)
                .contains("TRA-001", "TRA-002", "TRA-003");
    }

    @Test
    void getAllBookings_noTravelForDate_returns404() throws Exception {
        LocalDate futureDate = LocalDate.now().plusDays(100);

        mockMvc.perform(get("/api/trains/admin/bookings/TRA-001")
                        .param("date", futureDate.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllBookings_withExistingBooking_returnsItineraries() throws Exception {
        LocalDate futureDate = LocalDate.now().plusDays(14);
        if (futureDate.getDayOfWeek().name().matches("SATURDAY|SUNDAY")) {
            futureDate = futureDate.plusDays(2);
        }

        String body = """
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

        MvcResult result = mockMvc.perform(get("/api/trains/admin/bookings/TRA-002")
                        .param("date", futureDate.toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<ItineraryResponse> itineraries = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<ItineraryResponse>>() {});

        assertThat(itineraries).isNotEmpty();
        ItineraryResponse firstItinerary = itineraries.get(0);
        assertThat(firstItinerary.segments()).hasSize(1);
        assertThat(firstItinerary.segments().get(0).trainCode()).isEqualTo("TRA-002");
        assertThat(firstItinerary.segments().get(0).departureStation()).isEqualTo("Dej");
        assertThat(firstItinerary.segments().get(0).arrivalStation()).isEqualTo("Sibiu");
    }

    @Test
    void createTrain_appearsInAvailableTrains() throws Exception {
        LocalDate date = LocalDate.now().plusDays(7);

        String createBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(post("/api/trains/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/trains/available")
                        .param("from", "Cluj-Napoca")
                        .param("to", "Medias")
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<TrainResponse> trains = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TrainResponse>>() {});

        assertThat(trains).anyMatch(train -> train.trainCode().equals(NEW_TRAIN_CODE));
    }

    @Test
    void deleteTrain_bookingFails() throws Exception {
        LocalDate date = LocalDate.now().plusDays(7);

        String createBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(post("/api/trains/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/trains/admin/" + NEW_TRAIN_CODE))
                .andExpect(status().isNoContent());

        String bookingBody = """
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
        LocalDate date = LocalDate.now().plusDays(7);

        String createBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(post("/api/trains/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        String updateBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Dej", "Gherla", "Cluj-Napoca"],
                    "arrivals": {"Dej": "05:00", "Gherla": "05:25", "Cluj-Napoca": "06:00"},
                    "stopDurations": {"Dej": 0, "Gherla": 2, "Cluj-Napoca": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(put("/api/trains/admin/" + NEW_TRAIN_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        String oldBookingBody = """
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
        LocalDate date = LocalDate.now().plusDays(7);

        String createBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(post("/api/trains/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        String updateBody = """
                {
                    "trainCode": "%s",
                    "capacity": 100,
                    "stations": ["Dej", "Gherla", "Cluj-Napoca"],
                    "arrivals": {"Dej": "05:00", "Gherla": "05:25", "Cluj-Napoca": "06:00"},
                    "stopDurations": {"Dej": 0, "Gherla": 2, "Cluj-Napoca": 0},
                    "operatingDays": %s
                }
                """.formatted(NEW_TRAIN_CODE, objectMapper.writeValueAsString(ALL_DAYS));

        mockMvc.perform(put("/api/trains/admin/" + NEW_TRAIN_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        String newBookingBody = """
                {
                    "segments": [{"trainCode": "%s", "departureStation": "Dej", "arrivalStation": "Cluj-Napoca"}],
                    "travelDate": "%s",
                    "userEmail": "test@example.com",
                    "userName": "Test User"
                }
                """.formatted(NEW_TRAIN_CODE, date);

        MvcResult result = mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newBookingBody))
                .andExpect(status().isCreated())
                .andReturn();

        ItineraryResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ItineraryResponse.class);

        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).trainCode()).isEqualTo(NEW_TRAIN_CODE);
        assertThat(response.segments().get(0).departureStation()).isEqualTo("Dej");
        assertThat(response.segments().get(0).arrivalStation()).isEqualTo("Cluj-Napoca");
    }

    @Test
    void createTrain_unknownStation_returns400() throws Exception {
        String body = """
                {
                    "trainCode": "TRA-BAD",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Atlantis"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Atlantis": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Atlantis": 0},
                    "operatingDays": ["MONDAY"]
                }
                """;

        mockMvc.perform(post("/api/trains/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTrain_duplicateCode_returns409() throws Exception {
        String body = """
                {
                    "trainCode": "TRA-001",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda", "Medias"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
                    "operatingDays": ["MONDAY"]
                }
                """;

        mockMvc.perform(post("/api/trains/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteTrain_unknownCode_returns404() throws Exception {
        mockMvc.perform(delete("/api/trains/admin/TRA-NONEXISTENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTrain_unknownCode_returns404() throws Exception {
        String body = """
                {
                    "trainCode": "TRA-NONEXISTENT",
                    "capacity": 100,
                    "stations": ["Cluj-Napoca", "Turda"],
                    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30"},
                    "stopDurations": {"Cluj-Napoca": 0, "Turda": 0},
                    "operatingDays": ["MONDAY"]
                }
                """;

        mockMvc.perform(put("/api/trains/admin/TRA-NONEXISTENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
