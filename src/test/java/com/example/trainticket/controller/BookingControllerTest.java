package com.example.trainticket.controller;

import com.example.trainticket.dto.ItineraryResponse;
import com.example.trainticket.dto.RouteOption;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bookTicket_viaApi_returnsCreatedWithBookingDetails() throws Exception {
        LocalDate futureDate = LocalDate.now().plusDays(7);

        String body = """
                {
                    "segments": [
                        {
                            "trainCode": "TRA-001",
                            "departureStation": "Cluj-Napoca",
                            "arrivalStation": "Bucuresti"
                        }
                    ],
                    "travelDate": "%s",
                    "userEmail": "test@example.com",
                    "userName": "Test User"
                }
                """.formatted(futureDate);

        MvcResult result = mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        ItineraryResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ItineraryResponse.class);

        assertThat(response.id()).isNotNull();
        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).trainCode()).isEqualTo("TRA-001");
        assertThat(response.segments().get(0).departureStation()).isEqualTo("Cluj-Napoca");
        assertThat(response.segments().get(0).arrivalStation()).isEqualTo("Bucuresti");
        assertThat(response.travelDate()).isEqualTo(futureDate);
        assertThat(response.userName()).isEqualTo("Test User");
        assertThat(response.userEmail()).isEqualTo("test@example.com");
    }

    @Test
    void findRoutes_viaApi_returns200WithRouteOptions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/booking/routes")
                        .param("from", "Timisoara")
                        .param("to", "Bucuresti")
                        .param("date", LocalDate.now().plusDays(7).toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<RouteOption> routes = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<RouteOption>>() {});

        assertThat(routes).isNotEmpty();
        assertThat(routes.get(0).segments()).hasSize(2);
        assertThat(routes.get(0).segments().get(0).trainCode()).isEqualTo("TRA-005");
    }

    @Test
    void findRoutes_viaApi_unknownStation_returns404() throws Exception {
        mockMvc.perform(get("/api/booking/routes")
                        .param("from", "Atlantis")
                        .param("to", "Bucuresti")
                        .param("date", LocalDate.now().plusDays(7).toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void notifyDelay_withExistingBooking_returns200() throws Exception {
        LocalDate futureDate = LocalDate.now().plusDays(30);

        String bookingBody = """
                {
                    "segments": [
                        {
                            "trainCode": "TRA-001",
                            "departureStation": "Cluj-Napoca",
                            "arrivalStation": "Bucuresti"
                        }
                    ],
                    "travelDate": "%s",
                    "userEmail": "delay@test.com",
                    "userName": "Delay User"
                }
                """.formatted(futureDate);

        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/booking/admin/delay/TRA-001/{date}/30", futureDate.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void notifyDelay_noTravel_returns404() throws Exception {
        mockMvc.perform(post("/api/booking/admin/delay/TRA-001/2099-01-01/15"))
                .andExpect(status().isNotFound());
    }
}
