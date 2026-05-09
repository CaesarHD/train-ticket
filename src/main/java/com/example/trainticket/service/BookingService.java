package com.example.trainticket.service;

import com.example.trainticket.dto.*;
import com.example.trainticket.mapper.BookingMapper;
import com.example.trainticket.model.*;
import com.example.trainticket.repository.*;
import com.example.trainticket.validation.RouteValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final long TRANSFER_PENALTY_MINUTES = 15;
    private static final int MAX_ROUTE_OPTIONS = 5;

    private final TrainRepository trainRepository;
    private final TravelService travelService;
    private final BookingRepository bookingRepository;
    private final ItineraryRepository itineraryRepository;
    private final StationRepository stationRepository;
    private final RouteRepository routeRepository;
    private final RouteValidator routeValidator;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;

    public BookingResponse bookTicket(BookingRequest request) {
        log.info("bookTicket request: segments={}, date={}, email={}",
                request.segments(), request.travelDate(), request.userEmail());

        User user = userRepository.findByEmail(request.userEmail())
                .orElseGet(() -> {
                    log.info("User not found, creating: {}", request.userEmail());
                    return userRepository.save(new User(request.userEmail(), request.userName()));
                });

        Itinerary itinerary = itineraryRepository.save(new Itinerary(user));

        for (BookingSegment seg : request.segments()) {
            Train train = trainRepository.findByTrainCode(seg.trainCode())
                    .orElseThrow(() -> {
                        log.warn("Train not found: {}", seg.trainCode());
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Train not found: " + seg.trainCode());
                    });

            Station departure = stationRepository.findByName(seg.departureStation())
                    .orElseThrow(() -> {
                        log.warn("Departure station not found: {}", seg.departureStation());
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + seg.departureStation());
                    });
            Station arrival = stationRepository.findByName(seg.destinationStation())
                    .orElseThrow(() -> {
                        log.warn("Destination station not found: {}", seg.destinationStation());
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + seg.destinationStation());
                    });

            Route route = findRoute(departure, arrival);

            routeValidator.validateTravelDate(train, request.travelDate());
            routeValidator.validateTrainContainsRoute(train, route);

            Travel travel = travelService.findOrCreate(train, request.travelDate());
            routeValidator.validateSeatsAvailable(travel, route);
            travelService.bookSeat(travel, route);

            Booking booking = new Booking(travel, route, user);
            booking.setItinerary(itinerary);
            booking.setCreatedAt(LocalDateTime.now());
            bookingRepository.save(booking);
            itinerary.getBookings().add(booking);
            log.debug("Booking created for segment: train={}, route={}->{}",
                    train.getTrainCode(), departure.getName(), arrival.getName());
        }

        return bookingMapper.toResponse(itinerary);
    }

    private record Edge(String toStation, String trainCode, LocalDateTime departureTime, LocalDateTime arrivalTime) {}
    private record InternalSegment(String trainCode, String departureStation, String arrivalStation,
                                   LocalDateTime departureTime, LocalDateTime arrivalTime) {}
    private record Node(String station, LocalDateTime arrivalTime, List<InternalSegment> segments)
            implements Comparable<Node> {
        @Override
        public int compareTo(Node o) {
            return this.arrivalTime.compareTo(o.arrivalTime);
        }
    }

    public List<RouteOption> findRoutes(String from, String to, LocalDate date) {
        Station departure = stationRepository.findByName(from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + from));
        Station destination = stationRepository.findByName(to)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + to));

        if (departure.equals(destination)) return List.of();

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        List<Train> trains = trainRepository.findAll().stream()
                .filter(t -> {
                    var days = t.getOperatingDays();
                    return days == null || days.isEmpty() || days.contains(dayOfWeek);
                })
                .toList();

        if (trains.isEmpty()) return List.of();

        Map<String, List<Edge>> graph = buildGraph(trains, date);

        LocalDateTime startTime = date.atStartOfDay();
        PriorityQueue<Node> pq = new PriorityQueue<>();
        pq.add(new Node(departure.getName(), startTime, List.of()));

        List<RouteOption> results = new ArrayList<>();

        while (!pq.isEmpty() && results.size() < MAX_ROUTE_OPTIONS) {
            Node node = pq.poll();

            if (node.station.equals(destination.getName())) {
                long totalMin = Duration.between(startTime, node.arrivalTime).toMinutes();
                int transfers = Math.max(0, node.segments.size() - 1);
                results.add(new RouteOption(
                        node.segments.stream()
                                .map(s -> new SegmentOption(
                                        s.trainCode(), s.departureStation(), s.arrivalStation(),
                                        s.departureTime().format(TIME_FMT), s.arrivalTime().format(TIME_FMT)))
                                .toList(),
                        totalMin, transfers));
                continue;
            }

            String lastTrain = node.segments.isEmpty()
                    ? null : node.segments.get(node.segments.size() - 1).trainCode();

            for (Edge edge : graph.getOrDefault(node.station, List.of())) {
                boolean sameTrain = lastTrain != null && lastTrain.equals(edge.trainCode);

                if (!sameTrain && edge.departureTime.isBefore(node.arrivalTime)) continue;

                List<InternalSegment> newSegments = new ArrayList<>(node.segments);
                if (sameTrain && !newSegments.isEmpty()) {
                    InternalSegment last = newSegments.remove(newSegments.size() - 1);
                    newSegments.add(new InternalSegment(
                            edge.trainCode, last.departureStation(), edge.toStation,
                            last.departureTime(), edge.arrivalTime));
                } else {
                    newSegments.add(new InternalSegment(
                            edge.trainCode, node.station, edge.toStation,
                            edge.departureTime, edge.arrivalTime));
                }

                pq.add(new Node(edge.toStation, edge.arrivalTime, newSegments));
            }
        }

        results.sort(Comparator.comparing(RouteOption::totalMinutes));
        return results;
    }

    private Map<String, List<Edge>> buildGraph(List<Train> trains, LocalDate date) {
        Map<String, List<Edge>> graph = new HashMap<>();
        for (Train t : trains) {
            List<Station> stations = t.getRoute().getStations();
            for (int i = 0; i < stations.size() - 1; i++) {
                Station fromSt = stations.get(i);
                Station toSt = stations.get(i + 1);
                LocalDateTime refDep = t.getDepartureTimeFrom(fromSt);
                LocalDateTime refArr = t.getArrivalTimeFrom(toSt);
                if (refDep == null || refArr == null) continue;
                LocalDateTime depTime = LocalDateTime.of(date, refDep.toLocalTime());
                LocalDateTime arrTime = LocalDateTime.of(date, refArr.toLocalTime());
                if (arrTime.isBefore(depTime)) arrTime = arrTime.plusDays(1);
                graph.computeIfAbsent(fromSt.getName(), k -> new ArrayList<>())
                        .add(new Edge(toSt.getName(), t.getTrainCode(), depTime, arrTime));
            }
        }
        return graph;
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
                .map(Booking::getItinerary)
                .filter(Objects::nonNull)
                .distinct()
                .map(bookingMapper::toResponse)
                .toList();
    }

    public List<BookingResponse> getAllBookings() {
        return itineraryRepository.findAll().stream()
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
