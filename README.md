# train-ticket

A Java train ticketing application.
Built with Spring Boot 3.5 + JPA + H2 (or PostgreSQL) + MailHog for emails.

---

## How I designed the architecture

### Models — the main entities

| Entity | What it represents                                                                                                                                                                         |
|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Station** | A station (Cluj-Napoca, Bucuresti, etc.). Just name + ID.                                                                                                                                  |
| **Route** | An ordered list of stations. Example: `[Cluj, Turda, Medias, Brasov]`. A route can also be a subsequence (e.g. just `[Cluj, Medias]` is a subroute of the full route).                     |
| **Train** | A real train. Has a code (TRA-001), capacity, a route (Route), a map of arrivals per station, a map of stop durations per station, and operating days.                                     |
| **Travel** | The bridge between a train and a specific date. This holds the **remaining seats per subroute** (`Map<Route, Integer>`). When you book, seats get decremented from all affected subroutes. |
| **Booking** | One user's reservation on one segment (one train, one subroute, one date). If your journey has 3 trains → 3 Bookings.                                                                      |
| **Itinerary** | Groups multiple Bookings into one complete journey (with transfers).                                                                                                                       |
| **User** | Just email + name. Auto-created on first booking.                                                                                                                                          |

Relationship:

```
Train ─── Travel ─── Booking ─── Itinerary ─── User
             │           │
             │           └── route (booked subroute)
             │
             └── routeSeats (availability per subroute)
```

### Why Travel + Booking are separate

A `Travel = Train + date` holds availability for ALL subroutes of the train. A `Booking` is for ONE specific subroute.

Example: Train TRA-001 has route `[Cluj, Turda, Medias, Brasov]` and capacity 200.
A Travel for TRA-001 on May 18 stores:
```
{Cluj→Turda: 200, Cluj→Medias: 200, Cluj→Brasov: 200, Turda→Medias: 200, Turda→Brasov: 200, Medias→Brasov: 200}
```

When someone books `Cluj→Brasov`:
- All subroutes that overlap with `[Cluj, Turda, Medias, Brasov]` get decremented
- After booking: `{Cluj→Brasov: 199, Cluj→Turda: 199, Cluj→Medias: 199, Turda→Medias: 199, Turda→Brasov: 199, Medias→Brasov: 199}`

This is correct — the passenger occupies the seat for the whole ride, nobody else can board between Cluj and Brasov.

### How `bookSeat` works

```java
if (depIdx < rArr && rDep < arrIdx)
```

For a booking `[depIdx, arrIdx]` (e.g. 0→2 = Dej→Sighisoara), I decrement seats from any subroute `[rDep, rArr]` that overlaps:

```
Train route:  Dej ─── Medias ─── Sighisoara
                [0]       [1]        [2]

Booking Dej→Sighisoara [0, 2]:
  R0: Dej→Medias       0<1 && 0<2 → true  (decrement)
  R1: Medias→Sighisoara 0<2 && 1<2 → true  (decrement)
  R2: Dej→Sighisoara   0<2 && 0<2 → true  (decrement)

Booking Dej→Medias [0, 1]:
  R0: Dej→Medias       0<1 && 0<1 → true  (decrement)
  R1: Medias→Sighisoara 0<2 && 1<1 → false (unchanged ✓)
```

If a passenger gets off at Medias, the seat is free for someone else between Medias and Sighisoara.

---

## How I solved the tricky part — seat tracking per subroute

The hardest part was keeping correct seat counts when multiple routes overlap.

A Travel stores remaining seats for **every possible subroute**. When a booking comes in:

1. Find the most direct subroute between the requested stations (e.g. `Cluj→Brasov`)
2. Check the train includes this subroute
3. Check there are seats available
4. Decrement 1 seat from ALL subroutes that overlap with the booked segment

The intersection algorithm:

```
depIdx = index of departure station in the train's route
arrIdx = index of arrival station
rDep   = departure index of the affected subroute
rArr   = arrival index of the affected subroute

if (depIdx < rArr && rDep < arrIdx) → they intersect, decrement seat
```

This works correctly for bookings that share the same train on different segments.

---

## Dijkstra — how I find routes between stations

### Why Dijkstra and not something else

I chose **Dijkstra** because the problem is exactly a shortest-path in a time-weighted directed graph:

- Nodes are stations
- Edges are segments between consecutive stations, with timestamps
- I want to arrive at the destination as fast as possible, possibly with transfers

I used a modified version that accounts for:
- **Same train** → you can continue the journey without a break (extend the previous segment)
- **Different train** → you must wait until departure (15 min penalty per transfer)
- **Only trains operating on that day** are included in the graph

### How I build the graph

```java
for (Train train : trains) {
    for (consecutive stations on the train route) {
        // create edge: stationA → stationB, with trainCode, departureTime, arrivalTime
        // shift times from reference date (2025-01-01) to travel date
        // if arrival is before departure → overnight (+1 day)
    }
}
```

I only create edges between consecutive stations (not all pairs). The "continue on same train" optimization (extending the segment) keeps the state space small.

### How Dijkstra runs

1. **Start**: Initial state = `(departure_station, date@00:00, [])`
2. **Priority queue**: ordered by `arrivalTime` (earliest first)
3. For each edge from the current station:
   - **Same train**: extend the last segment (no transfer added)
   - **Different train**: create a new segment only if departure time >= arrival time
4. When I reach the destination: save the route
5. Always return at most 5 options, sorted by total time

---

## API — all endpoints

### 1. Book a ticket

```
POST http://localhost:8080/api/booking
Content-Type: application/json
```

**Input** — single train journey:
```json
{
    "segments": [{"trainCode": "TRA-001", "departureStation": "Cluj-Napoca", "arrivalStation": "Bucuresti"}],
    "travelDate": "2026-05-17",
    "userEmail": "test@example.com",
    "userName": "Test User"
}
```

**Output**:
```json
{
    "id": 2,
    "segments": [{
        "trainCode": "TRA-001",
        "departureStation": "Cluj-Napoca",
        "arrivalStation": "Bucuresti",
        "departureTime": "06:00",
        "arrivalTime": "13:00"
    }],
    "travelDate": "2026-05-17",
    "createdAt": "2026-05-10T21:42:18.577032",
    "userName": "Test User",
    "userEmail": "test@example.com"
}
```

**Input** — multi-train journey (Dej → Cluj → Bucuresti):
```json
{
    "segments": [
        {"trainCode": "TRA-002", "departureStation": "Dej", "arrivalStation": "Cluj-Napoca"},
        {"trainCode": "TRA-001", "departureStation": "Cluj-Napoca", "arrivalStation": "Bucuresti"}
    ],
    "travelDate": "2026-05-18",
    "userEmail": "user@mail.com",
    "userName": "User"
}
```

**Output**:
```json
{
    "id": 5,
    "segments": [
        {"trainCode": "TRA-002", "departureStation": "Dej", "arrivalStation": "Cluj-Napoca", "departureTime": "05:00", "arrivalTime": "06:00"},
        {"trainCode": "TRA-001", "departureStation": "Cluj-Napoca", "arrivalStation": "Bucuresti", "departureTime": "06:00", "arrivalTime": "13:00"}
    ],
    "travelDate": "2026-05-18",
    "createdAt": "2026-05-10T22:42:18.577032",
    "userName": "User",
    "userEmail": "user@mail.com"
}
```

After the booking is created, a confirmation email is sent asynchronously to the user's email via MailHog (SMTP). The app logs:
```
Confirmation email sent to user@mail.com
```

You can check the email at http://localhost:8025 (MailHog UI). The email subject is `"Booking Confirmation: 5"` and contains a short HTML body with the user's name.

#### Overbooking prevention

If you try to book a ticket on a route that has no remaining seats, the API returns **409 Conflict**:

```
POST http://localhost:8080/api/booking
Content-Type: application/json

{
    "segments": [{"trainCode": "T100", "departureStation": "Medias", "arrivalStation": "Sighisoara"}],
    "travelDate": "2026-05-10",
    "userEmail": "full@mail.com",
    "userName": "Full User"
}
```

**Output** (after all 100 seats have been booked):
```json
{
    "status": 409,
    "errors": ["No available seats for this route"],
    "timestamp": "2026-05-10T22:05:57.112"
}
```

The system tracks remaining seats per subroute inside a `Travel` entity. Each booking decrements overlapping subroutes. When `remainingSeats == 0`, the `RouteValidator` throws `ResponseStatusException(CONFLICT)`.

#### No route between stations

If no train can connect the departure and arrival stations (not even with transfers), the API returns **404 Not Found**:

```
GET http://localhost:8080/api/booking/routes?from=Satu%20Mare&to=Sinaia&date=2026-05-18
```

**Output**:
```json
{
    "status": 404,
    "errors": ["No route from Satu Mare to Sinaia"],
    "timestamp": "2026-05-10T22:42:18.577032"
}
```

The same error format applies for other validation failures:

| Scenario | HTTP Status | Error message |
|---|---|---|
| Overbooking | 409 | `No available seats for this route` |
| Train not found | 404 | `Train not found: TRA-XXX` |
| Station not found | 404 | `Station not found: XXX` |
| No route between stations | 404 | `No route from X to Y` |
| Route not a subroute | 400 | `Route is not a subroute of the train's route` |
| Invalid date (past or >1yr ahead) | 400 | `Not a valid date2026-05-09` |
| Train doesn't operate that day | 400 | `Train TRA-001 does not operate on SATURDAY` |
| Duplicate train code | 409 | `Train already exists: TRA-001` |

### 2. Find routes between stations

```
GET http://localhost:8080/api/booking/routes?from=Dej&to=Bucuresti&date=2026-05-11
```

This endpoint uses **Dijkstra** to find the fastest routes from Dej to Bucuresti on a Monday.

**Output** (4 options found):
```json
[
    {
        "segments": [
            {"trainCode": "TRA-002", "departureStation": "Dej", "arrivalStation": "Cluj-Napoca", "departureTime": "05:00", "arrivalTime": "06:00"},
            {"trainCode": "TRA-001", "departureStation": "Cluj-Napoca", "arrivalStation": "Bucuresti", "departureTime": "06:00", "arrivalTime": "13:00"}
        ],
        "totalMinutes": 780,
        "transfers": 1,
        "minRemainingSeats": 3
    },
    {
        "segments": [
            {"trainCode": "TRA-002", "departureStation": "Dej", "arrivalStation": "Turda", "departureTime": "05:00", "arrivalTime": "06:35"},
            {"trainCode": "TRA-001", "departureStation": "Turda", "arrivalStation": "Bucuresti", "departureTime": "06:35", "arrivalTime": "13:00"}
        ],
        "totalMinutes": 780,
        "transfers": 1,
        "minRemainingSeats": 8
    },
    {
        "segments": [
            {"trainCode": "TRA-002", "departureStation": "Dej", "arrivalStation": "Sibiu", "departureTime": "05:00", "arrivalTime": "08:15"},
            {"trainCode": "TRA-006", "departureStation": "Sibiu", "arrivalStation": "Bucuresti", "departureTime": "09:30", "arrivalTime": "13:30"}
        ],
        "totalMinutes": 810,
        "transfers": 1,
        "minRemainingSeats": 8
    },
    {
        "segments": [
            {"trainCode": "TRA-002", "departureStation": "Dej", "arrivalStation": "Cluj-Napoca", "departureTime": "05:00", "arrivalTime": "06:00"},
            {"trainCode": "TRA-001", "departureStation": "Cluj-Napoca", "arrivalStation": "Turda", "departureTime": "06:00", "arrivalTime": "06:30"},
            {"trainCode": "TRA-002", "departureStation": "Turda", "arrivalStation": "Sibiu", "departureTime": "06:37", "arrivalTime": "08:15"},
            {"trainCode": "TRA-006", "departureStation": "Sibiu", "arrivalStation": "Bucuresti", "departureTime": "09:30", "arrivalTime": "13:30"}
        ],
        "totalMinutes": 810,
        "transfers": 3,
        "minRemainingSeats": 8
    }
]
```

Direct route example (one transfer):
```
GET http://localhost:8080/api/booking/routes?from=Timisoara&to=Bucuresti&date=2026-05-12
```

**Output** — single transfer at Sibiu:
```json
[
    {
        "segments": [
            {"trainCode": "TRA-005", "departureStation": "Timisoara", "arrivalStation": "Sibiu", "departureTime": "06:30", "arrivalTime": "09:00"},
            {"trainCode": "TRA-006", "departureStation": "Sibiu", "arrivalStation": "Bucuresti", "departureTime": "09:30", "arrivalTime": "13:30"}
        ],
        "totalMinutes": 420,
        "transfers": 1,
        "minRemainingSeats": 8
    }
]
```

### 3. Find possible departure/arrival times between two stations

**Check which trains run on a given day between two stations, with remaining seats**:

```
GET http://localhost:8080/api/trains/available?from=Cluj-Napoca&to=Bucuresti&date=2026-05-17
```

**Output**:
```json
[
    {
        "trainCode": "TRA-001",
        "remainingSeats": 200,
        "departureStation": "Cluj-Napoca",
        "departureTime": "06:00",
        "arrivalStation": "Bucuresti",
        "arrivalTime": "13:00",
        "routeDeparture": "Cluj-Napoca",
        "routeDepartureTime": "06:00",
        "routeArrival": "Bucuresti",
        "routeArrivalTime": "13:00"
    }
]
```

Multiple options (Cluj → Turda, Monday):
```
GET http://localhost:8080/api/trains/available?from=Cluj-Napoca&to=Turda&date=2026-05-11
```

**Output** — 3 trains:
```json
[
    {"trainCode": "TRA-001", "remainingSeats": 200, "departureStation": "Cluj-Napoca", "departureTime": "06:00", "arrivalStation": "Turda", "arrivalTime": "06:30", ...},
    {"trainCode": "TRA-002", "remainingSeats": 150, "departureStation": "Cluj-Napoca", "departureTime": "06:00", "arrivalStation": "Turda", "arrivalTime": "06:35", ...},
    {"trainCode": "TRA-003", "remainingSeats": 180, "departureStation": "Cluj-Napoca", "departureTime": "08:45", "arrivalStation": "Turda", "arrivalTime": "09:20", ...}
]
```

> **Note on admin endpoints**: All administration endpoints live under the `/admin` path segment. Currently there is no authentication — a real login system with roles (e.g. Spring Security + JWT) would separate admin from regular users.

### 4. Route administration

**List all routes / Get one route by ID**:

```
GET http://localhost:8080/api/admin/routes
GET http://localhost:8080/api/admin/routes/1
```

**Create a new route** — provide an ordered list of station names:

```
POST http://localhost:8080/api/admin/routes
Content-Type: application/json

{"stations": ["Cluj-Napoca", "Turda", "Medias"]}
```

**Output**:
```json
{"id": 9, "stations": ["Cluj-Napoca", "Turda", "Medias"]}
```

**Update route stations** — only works if no trains are assigned to the route:

```
PUT http://localhost:8080/api/admin/routes/9
Content-Type: application/json

{"stations": ["Cluj-Napoca", "Dej", "Medias"]}
```

**Modify fails when trains are assigned** — if a route has trains, the update is rejected:

```
PUT http://localhost:8080/api/admin/routes/1
Content-Type: application/json

{"stations": ["Cluj-Napoca", "Dej"]}
```

**Output**:
```json
{
    "status": 409,
    "errors": ["Cannot modify route with 2 train(s) assigned"],
    "timestamp": "2026-05-10T22:42:18.577032"
}
```

You must delete the trains first before modifying the route.

```
DELETE http://localhost:8080/api/admin/routes/9
```

**Delete cascades to trains** — deleting a route also deletes all its trains (and their travels/bookings):

```
DELETE http://localhost:8080/api/admin/routes/1
```

After this call, both the route and its trains return 404:
```
GET http://localhost:8080/api/admin/routes/1       → 404
GET http://localhost:8080/api/trains/TRA-001        → 404
```

### 5. Train administration

**List all trains / Get one train by code**:

```
GET http://localhost:8080/api/trains
GET http://localhost:8080/api/trains/TRA-001
```

**Create a new train** — stations must be a subroute of an existing route, the route is auto-detected:

```
POST http://localhost:8080/api/trains/admin
Content-Type: application/json

{
    "trainCode": "TRA-NEW",
    "capacity": 100,
    "stations": ["Cluj-Napoca", "Turda", "Medias"],
    "arrivals": {"Cluj-Napoca": "06:00", "Turda": "06:30", "Medias": "07:00"},
    "stopDurations": {"Cluj-Napoca": 0, "Turda": 5, "Medias": 0},
    "operatingDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
}
```

**Output**:
```json
{"id": 9, "trainCode": "TRA-NEW", "capacity": 100, "routeId": 9, "operatingDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]}
```

**Update or delete a train**:

```
PUT http://localhost:8080/api/trains/admin/TRA-NEW
DELETE http://localhost:8080/api/trains/admin/TRA-NEW
```

**Delete train makes it unavailable** — after deleting a train, booking on it fails:

```
DELETE http://localhost:8080/api/trains/admin/TRA-NEW
```

```
POST http://localhost:8080/api/booking
Content-Type: application/json

{
    "segments": [{"trainCode": "TRA-NEW", "departureStation": "Cluj-Napoca", "arrivalStation": "Turda"}],
    "travelDate": "2026-05-18",
    "userEmail": "test@mail.com",
    "userName": "Test"
}
```

**Output**:
```json
{
    "status": 404,
    "errors": ["Train not found: TRA-NEW"],
    "timestamp": "2026-05-10T22:42:18.577032"
}
```

### 6. View all bookings for a train (admin)

```
GET http://localhost:8080/api/trains/admin/bookings/TRA-002?date=2026-05-18
```

### 7. Notify delay (admin)

```
POST http://localhost:8080/api/booking/admin/delay/TRA-001/2026-05-17/30
```
→ Sends email to all passengers of TRA-001 on May 17: "30 minute delay"

### 8. Booking history

```
GET http://localhost:8080/api/booking/all
GET http://localhost:8080/api/booking/user/1
```

---

## How to run

### With H2 (default, no setup)

```bash
./mvnw spring-boot:run
```

H2 console: http://localhost:8080/h2-console

### With PostgreSQL

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

### Emails with MailHog

```bash
docker compose up -d
```

MailHog UI: http://localhost:8025

The app sends `@Async` emails through MailHog (SMTP localhost:1025).

---

## Seed data

The app starts with 24 Romanian stations + Vienna, 8 routes and 8 predefined trains:

| Train | Route | Operating days |
|---|---|---|
| TRA-001 | Cluj → Turda → Medias → Sighisoara → Brasov → Sinaia → Ploiesti → Bucuresti | Daily |
| TRA-002 | Dej → Gherla → Cluj → Turda → Alba Iulia → Sibiu | Mon-Fri |
| TRA-003 | Oradea → Huedin → Cluj → Turda → Medias → Sighisoara | Mon, Wed, Fri, Sat |
| TRA-004 | Satu Mare → Baia Mare → Dej → Gherla → Cluj → Huedin → Oradea | Sat, Sun |
| TRA-005 | Vienna → Arad → Timisoara → Sibiu | Daily |
| TRA-006 | Sibiu → Ramnicu Valcea → Pitesti → Bucuresti | Daily |
| TRA-007 | Bucuresti → Buzau → Braila → Galati | Daily |
| TRA-008 | Brasov → Buzau → Braila → Galati | Daily |

Default user: `test@example.com` / "Test User"

---

## Validations and business rules

- **Overbooking**: cannot book if remaining seats on subroute is 0
- **Past date**: rejected
- **Date > 1 year ahead**: rejected (constant `BOOKING_THRESHOLD = 1`)
- **Duplicate stations on route**: rejected
- **Invalid subroute**: the chosen route must be a subroute of the train's route
- **Operating day**: the train must operate on that day
- **Duplicate train code**: conflict on creation
- **Delete route with trains**: associated trains and travels are deleted automatically

---

## Technical config

**Port**: `8080`

**Properties** (`src/main/resources/application.properties`):
```properties
spring.datasource.url=jdbc:h2:mem:train-ticket
spring.jpa.hibernate.ddl-auto=update
spring.mail.host=localhost
spring.mail.port=1025
```

For PostgreSQL, use the `postgres` profile.

---

## Tests

69 tests, all passing:

```bash
./mvnw test
```

Coverage: controllers (API), services (business logic + Dijkstra + booking), model (subroutes, schedule), email sender.

---

