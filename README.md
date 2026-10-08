# Traffic Engine

Speeding rules change over time, so a reading taken on 30 September must be fined by September's rules even if it reaches the server in October. Traffic Engine picks the rule set that was in force on the reading's date, works out the fine, and stores a citation that names the rule version and explains the amount.

![screenshot](screenshot.png)

## Run (macOS)

Needs Java 21 (`brew install --cask temurin@21`) and Docker Desktop.

```bash
docker run -d --name traffic-db -e POSTGRES_USER=traffic -e POSTGRES_PASSWORD=traffic \
  -e POSTGRES_DB=traffic -p 5432:5432 postgres:17
./mvnw spring-boot:run
```

Open <http://localhost:8080>. Flyway creates the `citations` table on the first start. `docker build -t trafficengine .` builds the container image.

## How it works

1. The dashboard sends `POST /api/readings` with a vehicle id, zone, speed and an optional observed time.
2. `CitationService` normalises the ids (`school zone` becomes `SCHOOL-ZONE`) and uses the current time if none was sent.
3. `RuleCatalog` returns the rule set whose start date is the latest one on or before the observed time. Rule sets live in `application.yml` and start at midnight India time.
4. `SpeedRuleEngine` reads the zone's limit (or the default limit) and returns `WITHIN_LIMIT`, `EXEMPT` for an emergency vehicle, or `VIOLATION` with the fine tier for how far over the limit it is.
5. A violation is saved as a `Citation` with its rule version and a one-line reason, and is never updated.

Example: 55 km/h in the school zone under the `2026-10` rules is limit 25, +30 over, above the +20 tier, so the fine is ₹3000. The same reading dated 30 September uses `2026-09`: limit 30, +25 over, fine ₹2000.

Speeds and fines are `BigDecimal` in Java and `numeric` in PostgreSQL, so there is no floating point in the money.

## API

| Method | Path | What it does |
| --- | --- | --- |
| `POST` | `/api/readings` | Judge one reading: 201 with the citation for a violation, 200 otherwise, 400 for bad input |
| `GET` | `/api/citations?zone=` | The newest 50 citations, optionally for one zone |
| `GET` | `/api/citations/{id}` | One citation with its reason, or 404 |
| `GET` | `/api/analytics/summary` | Citation count and fine total per zone |

```json
POST /api/readings
{ "vehicleId": "KA 01 AB 1234", "zone": "school zone", "speedKph": 55 }
```

Errors are Spring `ProblemDetail` JSON; a 400 lists each rejected field.

## Tests

```bash
./mvnw test
```

Rule engine unit tests and one `@WebMvcTest` for the API. No database or Docker needed.
