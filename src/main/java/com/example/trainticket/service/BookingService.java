package com.example.trainticket.service;

import com.example.trainticket.dto.*;
import com.example.trainticket.mapper.BookingMapper;
import com.example.trainticket.model.*;
import com.example.trainticket.repository.*;
import com.example.trainticket.validation.RouteValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

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
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public ItineraryResponse bookTicket(ItineraryRequest request) {

        log.info("bookTicket request: segments={}, date={}, email={}",
                request.segments(), request.travelDate(), request.userEmail());

        User user = getUser(request);
        Itinerary itinerary = itineraryRepository.save(new Itinerary(user));
        LocalDate travelDate = request.travelDate();

        for (BookingSegment seg : request.segments()) {
            Booking booking = createBooking(seg, travelDate, user, itinerary);
            bookingRepository.save(booking);
            itinerary.getBookings().add(booking);

            log.debug("Booking created for segment: train={}, route={}->{}",
                    booking.getTrainCode(),
                    booking.getDepartureStationName(),
                    booking.getArrivalStationName());
        }

        ItineraryResponse response = bookingMapper.toResponse(itinerary);

        try {
            String jsonData = objectMapper.writeValueAsString(response);
            emailService.sendConfirmation(user.getEmail(), user.getName(), itinerary.getId(), jsonData);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize itinerary for email", e);
        }

        return response;
    }

    private User getUser(ItineraryRequest request) {
        return userRepository.findByEmail(request.userEmail())
                .orElseGet(() -> {
                    log.info("User not found, creating: {}", request.userEmail());
                    return userRepository.save(new User(request.userEmail(), request.userName()));
                });
    }

    private Booking createBooking(BookingSegment seg, LocalDate travelDate, User user, Itinerary itinerary) {
        Train train = findTrain(seg.trainCode());
        Station departure = findStation(seg.departureStation());
        Station arrival = findStation(seg.arrivalStation());
        Route route = findMostDirectRoute(departure, arrival);

        routeValidator.validateTravelDate(train, travelDate);
        routeValidator.validateTrainContainsRoute(train, route);

        Travel travel = travelService.findOrCreate(train, travelDate);
        routeValidator.validateSeatsAvailable(travel, route);
        travelService.bookSeat(travel, route);

        Booking booking = new Booking(travel, route, user);
        booking.setItinerary(itinerary);
        booking.setCreatedAt(LocalDateTime.now());

        return booking;
    }

    private Station findStation(String stationName) {
        return stationRepository.findByName(stationName)
                .orElseThrow(() -> {
                    log.warn("Station not found: {}", stationName);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + stationName);
                });
    }

    private Train findTrain(String trainCode) {
        return trainRepository.findByTrainCode(trainCode)
                .orElseThrow(() -> {
                    log.warn("Train not found: {}", trainCode);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Train not found: " + trainCode);
                });
    }

    private record Edge(String toStation, String trainCode, LocalDateTime departureTime, LocalDateTime arrivalTime) {
    }

    private record InternalSegment(String trainCode, String departureStation, String arrivalStation,
                                   LocalDateTime departureTime, LocalDateTime arrivalTime) {
    }

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

        List<Train> trains = getOperatingTrains(date);
        if (trains.isEmpty()) return List.of();

        Map<String, Train> trainByCode = trains.stream()
                .collect(Collectors.toMap(Train::getTrainCode, t -> t));

        List<Route> allRoutes = routeRepository.findAllWithStations();
        Map<String, Travel> travelCache = new HashMap<>();
        Map<String, List<Edge>> graph = buildGraph(trains, date);

        LocalDateTime startTime = date.atStartOfDay();
        PriorityQueue<Node> pq = new PriorityQueue<>();
        pq.add(new Node(departure.getName(), startTime, List.of()));

        List<RouteOption> results = new ArrayList<>();

        while (!pq.isEmpty() && results.size() < MAX_ROUTE_OPTIONS) {
            Node node = pq.poll();

            if (node.station.equals(destination.getName())) {
                results.add(toRouteOption(node, startTime, date, trainByCode, allRoutes, travelCache));
                continue;
            }

            String lastTrain = node.segments.isEmpty()
                    ? null : node.segments.get(node.segments.size() - 1).trainCode();

            for (Edge edge : graph.getOrDefault(node.station, List.of())) {
                boolean sameTrain = lastTrain != null && lastTrain.equals(edge.trainCode);
                if (!sameTrain && edge.departureTime.isBefore(node.arrivalTime)) continue;

                List<InternalSegment> newSegments = appendSegment(node.segments, edge, node.station, sameTrain);
                pq.add(new Node(edge.toStation, edge.arrivalTime, newSegments));
            }
        }

        results.sort(Comparator.comparing(RouteOption::totalMinutes));
        return results;
    }

    private List<Train> getOperatingTrains(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return trainRepository.findAll().stream()
                .filter(t -> {
                    var days = t.getOperatingDays();
                    return days == null || days.isEmpty() || days.contains(dayOfWeek);
                })
                .toList();
    }

    private RouteOption toRouteOption(Node node, LocalDateTime startTime, LocalDate date,
                                      Map<String, Train> trainByCode, List<Route> allRoutes,
                                      Map<String, Travel> travelCache) {
        long totalMin = Duration.between(startTime, node.arrivalTime).toMinutes();
        int transfers = Math.max(0, node.segments.size() - 1);
        int minSeats = computeMinRemainingSeats(node.segments, date, trainByCode, allRoutes, travelCache);
        return new RouteOption(
                node.segments.stream()
                        .map(s -> new SegmentOption(
                                s.trainCode(), s.departureStation(), s.arrivalStation(),
                                s.departureTime().format(TIME_FMT), s.arrivalTime().format(TIME_FMT)))
                        .toList(),
                totalMin, transfers, minSeats);
    }

    private int computeMinRemainingSeats(List<InternalSegment> segments, LocalDate date,
                                         Map<String, Train> trainByCode, List<Route> allRoutes,
                                         Map<String, Travel> travelCache) {
        int min = Integer.MAX_VALUE;
        for (InternalSegment seg : segments) {
            Train t = trainByCode.get(seg.trainCode());
            if (t == null) continue;
            Route r = findMatchingRoute(allRoutes, t, seg.departureStation(), seg.arrivalStation());
            if (r == null) continue;
            String cacheKey = t.getTrainCode() + ":" + date;
            Travel travel = travelCache.computeIfAbsent(cacheKey, k -> travelService.findOrCreate(t, date));
            min = Math.min(min, travel.getRouteSeats().getOrDefault(r, 0));
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private List<InternalSegment> appendSegment(List<InternalSegment> segments, Edge edge,
                                                String currentStation, boolean sameTrain) {
        List<InternalSegment> newSegments = new ArrayList<>(segments);
        if (sameTrain && !newSegments.isEmpty()) {
            InternalSegment last = newSegments.remove(newSegments.size() - 1);
            newSegments.add(new InternalSegment(
                    edge.trainCode, last.departureStation(), edge.toStation,
                    last.departureTime(), edge.arrivalTime));
        } else {
            newSegments.add(new InternalSegment(
                    edge.trainCode, currentStation, edge.toStation,
                    edge.departureTime, edge.arrivalTime));
        }
        return newSegments;
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

    public Route findMostDirectRoute(Station departure, Station arrival) {
        return routeRepository.findAllWithStations().stream()
                .filter(r -> !r.getStations().isEmpty()
                        && r.getStations().getFirst().equals(departure)
                        && r.getStations().getLast().equals(arrival))
                .min(Comparator.comparingInt(r -> r.getStations().size()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No route from " + departure.getName() + " to " + arrival.getName()));
    }

    public List<ItineraryResponse> getAllUserBookings(Long id) {
        User user = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User with id " + id + " not found"));

        return bookingRepository.findByUser(user).stream()
                .map(Booking::getItinerary)
                .filter(Objects::nonNull)
                .distinct()
                .map(bookingMapper::toResponse)
                .toList();
    }

    public List<ItineraryResponse> getAllBookings() {
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
            route = findMostDirectRoute(departure, arrival);
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

    private Route findMatchingRoute(List<Route> allRoutes, Train train, String departureStation, String arrivalStation) {
        return allRoutes.stream()
                .filter(r -> {
                    List<Station> stations = r.getStations();
                    return stations.size() >= 2
                            && stations.getFirst().getName().equals(departureStation)
                            && stations.getLast().getName().equals(arrivalStation)
                            && train.isSubroute(r);
                })
                .findFirst()
                .orElse(null);
    }
}
