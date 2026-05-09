package com.example.trainticket.controller;

import com.example.trainticket.dto.BookingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
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
        var futureDate = LocalDate.now().plusDays(7);

        var body = """
                {
                    "trainCode": "TRA-001",
                    "departureStation": "Cluj-Napoca",
                    "destinationStation": "Bucuresti",
                    "travelDate": "%s",
                    "userEmail": "test@example.com",
                    "userName": "Test User"
                }
                """.formatted(futureDate);

        var result = mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                BookingResponse.class);

        assertThat(response.id()).isNotNull();
        assertThat(response.trainCode()).isEqualTo("TRA-001");
        assertThat(response.departureStation()).isEqualTo("Cluj-Napoca");
        assertThat(response.destinationStation()).isEqualTo("Bucuresti");
        assertThat(response.travelDate()).isEqualTo(futureDate);
        assertThat(response.userName()).isEqualTo("Test User");
        assertThat(response.userEmail()).isEqualTo("test@example.com");
    }
}
