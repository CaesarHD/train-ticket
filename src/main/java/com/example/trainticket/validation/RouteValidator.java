package com.example.trainticket.validation;

import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;
import com.example.trainticket.model.Train;
import com.example.trainticket.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class RouteValidator {

    public void validateSubroute(Train train, Route route) {
        if (!train.isSubroute(route)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Route is not a subroute of the train's route");
        }
    }

    public void validateSeatsAvailable(Train train, Route route) {
        int bookedSeats = train.getRouteSeats().getOrDefault(route, 0);
        if (bookedSeats >= train.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No available seats for this route");
        }
    }
}
