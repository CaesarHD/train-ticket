package com.example.trainticket.service;

import com.example.trainticket.dto.BookingRequest;
import com.example.trainticket.dto.BookingResponse;
import com.example.trainticket.dto.TrainResponse;
import com.example.trainticket.mapper.BookingMapper;
import com.example.trainticket.model.Booking;
import com.example.trainticket.model.Route;
import com.example.trainticket.model.Station;
import com.example.trainticket.model.Train;
import com.example.trainticket.model.Travel;
import com.example.trainticket.model.User;
import com.example.trainticket.repository.BookingRepository;
import com.example.trainticket.repository.RouteRepository;
import com.example.trainticket.repository.StationRepository;
import com.example.trainticket.repository.TrainRepository;
import com.example.trainticket.repository.UserRepository;
import com.example.trainticket.validation.RouteValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final TrainRepository trainRepository;
    private final TravelService travelService;
    private final BookingRepository bookingRepository;
    private final StationRepository stationRepository;
    private final RouteRepository routeRepository;
    private final RouteValidator routeValidator;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;

    public BookingResponse bookTicket(BookingRequest request) {
        log.info("bookTicket request: trainCode={}, from={}, to={}, date={}, email={}",
                request.trainCode(), request.departureStation(),
                request.destinationStation(), request.travelDate(), request.userEmail());

        Train train = trainRepository.findByTrainCode(request.trainCode())
                .orElseThrow(() -> {
                    log.warn("Train not found: {}", request.trainCode());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Train not found: " + request.trainCode());
                });
        log.debug("Found train: {} (capacity={})", train.getTrainCode(), train.getCapacity());

        Station departure = stationRepository.findByName(request.departureStation())
                .orElseThrow(() -> {
                    log.warn("Departure station not found: {}", request.departureStation());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + request.departureStation());
                });
        Station arrival = stationRepository.findByName(request.destinationStation())
                .orElseThrow(() -> {
                    log.warn("Destination station not found: {}", request.destinationStation());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + request.destinationStation());
                });
        log.debug("Route: {} -> {}", departure.getName(), arrival.getName());

        User user = userRepository.findByEmail(request.userEmail())
                .orElseGet(() -> {
                    log.info("User not found, creating: {}", request.userEmail());
                    return userRepository.save(new User(request.userEmail(), request.userEmail()));
                });
        log.debug("User: {} (id={})", user.getEmail(), user.getId());

        Route route = findRoute(departure, arrival);
        log.debug("Matched route: {} -> {} ({} stations)",
                route.getDeparture().getName(), route.getArrival().getName(), route.getStations().size());

        routeValidator.validateTravelDate(train, request.travelDate());
        routeValidator.validateTrainContainsRoute(train, route);

        Travel travel = travelService.findOrCreate(train, request.travelDate());
        routeValidator.validateSeatsAvailable(travel, route);

        travelService.bookSeat(travel, route);

        Booking booking = new Booking(travel, route, user);
        booking.setCreatedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);
        log.info("Booking created: id={}, train={}, route={}->{}, date={}, user={}",
                booking.getId(), train.getTrainCode(),
                departure.getName(), arrival.getName(),
                request.travelDate(), user.getEmail());

        return bookingMapper.toResponse(booking);
    }

    public Route findRoute(Station departure, Station arrival) {
        return routeRepository.findAllWithStations().stream()
                .filter(r -> !r.getStations().isEmpty()
                        && r.getStations().getFirst().equals(departure)
                        && r.getStations().getLast().equals(arrival))
                .min(Comparator.comparingInt(r -> r.getStations().size()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No route from " + departure.getName() + " to " + arrival.getName()));
    }

    public List<BookingResponse> getAllUserBookings(Long id) {
        User user = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User with id " + id + " not found"));

        return bookingRepository.findByUser(user).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    public List<BookingResponse> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    public List<TrainResponse> getAvailableTrains(String from, String to, LocalDate date) {
        Station departure = stationRepository.findByName(from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + from));
        Station arrival = stationRepository.findByName(to)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + to));

        Route route;
        try {
            route = findRoute(departure, arrival);
        } catch (ResponseStatusException e) {
            return List.of();
        }

        var dayOfWeek = date.getDayOfWeek();

        return trainRepository.findAll().stream()
                .filter(train -> {
                    var days = train.getOperatingDays();
                    if (days != null && !days.isEmpty() && !days.contains(dayOfWeek)) return false;
                    return train.isSubroute(route);
                })
                .map(train -> {
                    Travel travel = travelService.findOrCreate(train, date);
                    return TrainResponse.from(train, travel, route);
                })
                .toList();
    }
}
