package com.example.trainticket.validation;

import com.example.trainticket.model.Route;
import com.example.trainticket.model.Train;
import com.example.trainticket.model.Travel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import static com.example.trainticket.util.Constants.BOOKING_THRESHOLD;

@Component
@RequiredArgsConstructor
public class RouteValidator {

    public void validateTrainContainsRoute(Train train, Route route) {
        if (!train.isSubroute(route)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Route is not a subroute of the train's route");
        }
    }

    public void validateSeatsAvailable(Travel travel, Route route) {
        int remaining = travel.getRouteSeats().getOrDefault(route, 0);
        if (remaining <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No available seats for this route");
        }
    }

    public void validateTravelDate(Train train, LocalDate travelDate) {
        LocalDate now = LocalDate.now();
        if(travelDate.isBefore(now) || (travelDate.getYear() - now.getYear()) > BOOKING_THRESHOLD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Not a valid date" + travelDate);
        }
        validateOperatingDay(train, travelDate);
    }

    public void validateOperatingDay(Train train, LocalDate travelDate) {
        Set<DayOfWeek> days = train.getOperatingDays();
        if (days != null && !days.isEmpty() && !days.contains(travelDate.getDayOfWeek())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Train " + train.getTrainCode() + " does not operate on " + travelDate.getDayOfWeek());
        }
    }
}
