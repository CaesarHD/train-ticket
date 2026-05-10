package com.example.trainticket.controller;

import com.example.trainticket.dto.RouteInfo;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
@Transactional
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAll_returnsAllRoutes() throws Exception {
        var result = mockMvc.perform(get("/api/admin/routes"))
                .andExpect(status().isOk())
                .andReturn();

        var routes = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<RouteInfo>>() {});

        assertThat(routes).isNotEmpty();
    }

    @Test
    void getById_returnsRoute() throws Exception {
        var result = mockMvc.perform(get("/api/admin/routes/1"))
                .andExpect(status().isOk())
                .andReturn();

        var route = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                RouteInfo.class);

        assertThat(route.id()).isEqualTo(1L);
        assertThat(route.stations()).isNotEmpty();
    }

    @Test
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/admin/routes/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_withNewStations_createsStationsAndRoute() throws Exception {
        var body = """
                {
                    "stations": ["TestCityA", "TestCityB", "TestCityC"]
                }
                """;

        var result = mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var route = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                RouteInfo.class);

        assertThat(route.id()).isNotNull();
        assertThat(route.stations()).containsExactly("TestCityA", "TestCityB", "TestCityC");
    }

    @Test
    void create_withExistingStations_returnsRoute() throws Exception {
        var body = """
                {
                    "stations": ["Cluj-Napoca", "Turda", "Medias"]
                }
                """;

        var result = mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var route = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                RouteInfo.class);

        assertThat(route.stations()).containsExactly("Cluj-Napoca", "Turda", "Medias");
    }

    @Test
    void create_singleStation_returns400() throws Exception {
        var body = """
                {
                    "stations": ["Cluj-Napoca"]
                }
                """;

        mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateStations_returns400() throws Exception {
        var body = """
                {
                    "stations": ["Cluj-Napoca", "Cluj-Napoca"]
                }
                """;

        mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_routeWithNoTrains_updatesStations() throws Exception {
        var body = """
                {
                    "stations": ["TestCityA", "TestCityB", "TestCityC"]
                }
                """;

        var createResult = mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                RouteInfo.class);

        var updateBody = """
                {
                    "stations": ["TestCityA", "TestCityD", "TestCityC"]
                }
                """;

        var updateResult = mockMvc.perform(put("/api/admin/routes/" + created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andReturn();

        var updated = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(),
                RouteInfo.class);

        assertThat(updated.stations()).containsExactly("TestCityA", "TestCityD", "TestCityC");
    }

    @Test
    void update_routeWithTrains_returns409() throws Exception {
        var routeBody = """
                {
                    "stations": ["Viena", "Arad", "Timisoara"]
                }
                """;

        var createResult = mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody))
                .andExpect(status().isCreated())
                .andReturn();

        var created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                RouteInfo.class);

        var trainBody = """
                {
                    "trainCode": "TRA-ROUTE-TEST",
                    "capacity": 100,
                    "stations": ["Viena", "Arad", "Timisoara"],
                    "arrivals": {"Viena": "04:00", "Arad": "05:30", "Timisoara": "06:30"},
                    "stopDurations": {"Viena": 0, "Arad": 5, "Timisoara": 0},
                    "operatingDays": ["MONDAY"]
                }
                """;

        mockMvc.perform(post("/api/trains/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainBody))
                .andExpect(status().isCreated());

        var updateBody = """
                {
                    "stations": ["Viena", "Timisoara"]
                }
                """;

        mockMvc.perform(put("/api/admin/routes/" + created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_routeWithNoTrains_succeeds() throws Exception {
        var body = """
                {
                    "stations": ["TestCityA", "TestCityB"]
                }
                """;

        var createResult = mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                RouteInfo.class);

        mockMvc.perform(delete("/api/admin/routes/" + created.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/routes/" + created.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_routeWithTrains_deletesTrainsAndRoute() throws Exception {
        var routeBody = """
                {
                    "stations": ["TestCityA", "TestCityB", "TestCityC"]
                }
                """;

        var createResult = mockMvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody))
                .andExpect(status().isCreated())
                .andReturn();

        var created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                RouteInfo.class);

        var trainBody = """
                {
                    "trainCode": "TRA-ROUTE-DEL",
                    "capacity": 50,
                    "stations": ["TestCityA", "TestCityB", "TestCityC"],
                    "arrivals": {"TestCityA": "06:00", "TestCityB": "06:30", "TestCityC": "07:00"},
                    "stopDurations": {"TestCityA": 0, "TestCityB": 5, "TestCityC": 0},
                    "operatingDays": ["MONDAY"]
                }
                """;

        mockMvc.perform(post("/api/trains/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainBody))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/routes/" + created.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/routes/" + created.id()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/trains/TRA-ROUTE-DEL"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/api/admin/routes/9999"))
                .andExpect(status().isNotFound());
    }
}
