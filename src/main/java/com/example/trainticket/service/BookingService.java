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
    private final TravelRepository travelRepository;
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
        travel.getBookings().add(booking);

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

    // Dijkstra algorithm for finding the fastest route
    public List<RouteOption> findRoutes(String from, String to, LocalDate date) {
        Station departure = stationRepository.findByName(from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + from));
        Station destination = stationRepository.findByName(to)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found: " + to));

        if (departure.equals(destination)) {
            return List.of();
        }

        List<Train> trains = getOperatingTrains(date);
        if (trains.isEmpty()) {
            return List.of();
        }

        Map<String, Train> trainByCode = trains.stream()
                .collect(Collectors.toMap(Train::getTrainCode, train -> train));

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
                if (!sameTrain && edge.departureTime.isBefore(node.arrivalTime)) {
                    continue;
                }

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
                .filter(train -> {
                    Set<DayOfWeek> days = train.getOperatingDays();
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
                        .map(seg -> new SegmentOption(
                                seg.trainCode(), seg.departureStation(), seg.arrivalStation(),
                                seg.departureTime().format(TIME_FMT), seg.arrivalTime().format(TIME_FMT)))
                        .toList(),
                totalMin, transfers, minSeats);
    }

    private int computeMinRemainingSeats(List<InternalSegment> segments, LocalDate date,
                                         Map<String, Train> trainByCode, List<Route> allRoutes,
                                         Map<String, Travel> travelCache) {
        int min = Integer.MAX_VALUE;
        for (InternalSegment seg : segments) {
            Train train = trainByCode.get(seg.trainCode());
            if (train == null) {
                continue;
            }
            Route route = findMatchingRoute(allRoutes, train, seg.departureStation(), seg.arrivalStation());
            if (route == null) {
                continue;
            }
            String cacheKey = train.getTrainCode() + ":" + date;
            Travel travel = travelCache.computeIfAbsent(cacheKey, k -> travelService.findOrCreate(train, date));
            min = Math.min(min, travel.getRouteSeats().getOrDefault(route, 0));
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
        for (Train train : trains) {
            List<Station> stations = train.getRoute().getStations();
            for (int idx = 0; idx < stations.size() - 1; idx++) {
                Station fromSt = stations.get(idx);
                Station toSt = stations.get(idx + 1);
                LocalDateTime refDep = train.getDepartureTimeFrom(fromSt);
                LocalDateTime refArr = train.getArrivalTimeFrom(toSt);
                if (refDep == null || refArr == null) {
                    continue;
                }
                LocalDateTime depTime = LocalDateTime.of(date, refDep.toLocalTime());
                LocalDateTime arrTime = LocalDateTime.of(date, refArr.toLocalTime());
                if (arrTime.isBefore(depTime)) {
                    arrTime = arrTime.plusDays(1);
                }
                graph.computeIfAbsent(fromSt.getName(), k -> new ArrayList<>())
                        .add(new Edge(toSt.getName(), train.getTrainCode(), depTime, arrTime));
            }
        }
        return graph;
    }

    public Route findMostDirectRoute(Station departure, Station arrival) {
        return routeRepository.findAllWithStations().stream()
                .filter(route -> !route.getStations().isEmpty()
                        && route.getStations().getFirst().equals(departure)
                        && route.getStations().getLast().equals(arrival))
                .min(Comparator.comparingInt(route -> route.getStations().size()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No route from " + departure.getName() + " to " + arrival.getName()));
    }

    public List<ItineraryResponse> getAllTrainBookings(String trainCode, LocalDate date) {
        Train train = findTrain(trainCode);
        Travel travel = findTravel(train, date);
        return travel.getBookings().stream()
                .map(Booking::getItinerary)
                .filter(Objects::nonNull)
                .distinct()
                .map(bookingMapper::toResponse)
                .toList();
    }

    public void notifyDelay(String trainCode, LocalDate date, int minutes) {
        Train train = findTrain(trainCode);
        Travel travel = findTravel(train, date);

        Set<User> notified = new HashSet<>();
        for (Booking booking : travel.getBookings()) {
            User user = booking.getUser();
            if (user != null && notified.add(user)) {
                emailService.sendDelayNotification(user.getEmail(), user.getName(), trainCode, minutes);
            }
        }

        log.info("Delay notification sent to {} users for train {} on {}", notified.size(), trainCode, date);
    }

    private Travel findTravel(Train train, LocalDate date) {
        return travelRepository.findByTrainAndTravelDate(train, date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No travel found for " + train.getTrainCode() + " on " + date));
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

        DayOfWeek dayOfWeek = date.getDayOfWeek();

        return trainRepository.findAll().stream()
                .filter(train -> {
                    Set<DayOfWeek> days = train.getOperatingDays();
                    if (days != null && !days.isEmpty() && !days.contains(dayOfWeek)) {
                        return false;
                    }
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
                .filter(route -> {
                    List<Station> stations = route.getStations();
                    return stations.size() >= 2
                            && stations.getFirst().getName().equals(departureStation)
                            && stations.getLast().getName().equals(arrivalStation)
                            && train.isSubroute(route);
                })
                .findFirst()
                .orElse(null);
    }
}
