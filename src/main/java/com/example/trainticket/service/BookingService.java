package com.example.trainticket.service;

import com.example.trainticket.model.Booking;
import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;
import com.example.trainticket.model.Train;
import com.example.trainticket.repository.BookingRepository;
import com.example.trainticket.repository.RouteRepository;
import com.example.trainticket.repository.StationRepository;
import com.example.trainticket.repository.TrainRepository;
import com.example.trainticket.validation.RouteValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final TrainRepository trainRepository;
    private final BookingRepository bookingRepository;
    private final StationRepository stationRepository;
    private final RouteRepository routeRepository;
    private final RouteValidator routeValidator;

    public Booking bookTicket(String trainCode, String startStation, String finishStation, String passengerName) {
        Train train = trainRepository.findByTrainCode(trainCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Train not found: " + trainCode));
        Station departure = stationRepository.findByName(startStation)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + startStation));
        Station arrival = stationRepository.findByName(finishStation)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + finishStation));
        Route route = findRoute(departure, arrival);

        routeValidator.validateSubroute(train, route);
        routeValidator.validateSeatsAvailable(train, route);

        Booking booking = new Booking(train, route, passengerName);
        booking = bookingRepository.save(booking);

        train.bookSeat(route);
        trainRepository.save(train);

        return booking;
    }

    public Route findRoute(Station departure, Station arrival) {
        return routeRepository.findAll().stream()
                .filter(r -> !r.getStations().isEmpty()
                        && r.getStations().getFirst().equals(departure)
                        && r.getStations().getLast().equals(arrival))
                .min(Comparator.comparingInt(r -> r.getStations().size()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No route from " + departure.getName() + " to " + arrival.getName()));
    }
}
