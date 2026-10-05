# Traffic Rule Engine

[![tests](https://github.com/Krish3101/trafficengine/actions/workflows/tests.yml/badge.svg)](https://github.com/Krish3101/trafficengine/actions/workflows/tests.yml)

Fines follow the rule set in force when the reading was observed; every citation records the version, limit and tier that produced it.

Speeding rules change over time. A reading taken on 30 September must be judged by September's rules, even if it reaches the server in October. This service takes a speed reading, picks the versioned rule set by the reading's `observedAt`, works out the fine with exact decimals in a plain-Java engine, and stores a citation that explains itself.

Readings are simulated (typed into the dashboard or sent with curl); there is no camera integration.

**Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL, Flyway, JUnit 5, Testcontainers, Docker, Render.

**Live demo:** <https://traffic-app-oxw2.onrender.com> (free hosting, so the first load can take up to two minutes while it wakes up).

![Dashboard with a reading just evaluated and the citations recorded so far](docs/dashboard.png)

Three outcomes:

- `WITHIN_LIMIT`: at or under the limit, nothing stored
- `EXEMPT`: over the limit but an emergency vehicle, nothing stored
- `VIOLATION`: over the limit, a citation is stored with its fine and the reason

## Rule model

All rules live in `src/main/resources/application.yml` as a list of versioned rule sets:

```yaml
traffic:
  jurisdiction-time-zone: Asia/Kolkata
  max-reading-age: P90D
  max-clock-skew: PT5M
  rule-sets:
    - version: "2026-09"
      effective-from: 2026-01-01
      currency: INR
      default-speed-limit-kph: 80.00
      zones:
        - id: SCHOOL-ZONE
          limit-kph: 30.00
        - id: HIGHWAY-1
          limit-kph: 100.00
      fine-tiers:
        - over-by-kph: 0.00
          amount: 1000.00
        - over-by-kph: 20.00
          amount: 2000.00
        - over-by-kph: 40.00
          amount: 5000.00
    - version: "2026-10"
      effective-from: 2026-10-01
      # same shape: SCHOOL-ZONE 25 km/h, fines 1500 / 3000 / 6000
```

- The shipped sets are `2026-09` (from 2026-01-01) and `2026-10` (from 2026-10-01; school zone 25 km/h and higher fines).
- A rule set applies from midnight of its `effective-from` date in Asia/Kolkata until the next set starts (a half-open interval). So `2026-09-30T23:59:59+05:30` uses `2026-09` and `2026-10-01T00:00:00+05:30` uses `2026-10`.
- `observedAt` is optional (it defaults to now). Readings may be up to 90 days old and up to 5 minutes in the future; anything else is rejected with 422.
- Tier edges are strict: exactly +20 over stays in the +0 tier, +20.01 reaches the +20 tier.
- A zone that is not listed uses `default-speed-limit-kph` (the general limit for unlisted roads). The response then has `"defaultLimit": true` and the dashboard shows a note, so a typo in the zone is visible.
- Zone and vehicle ids are canonicalised (`school zone` → `SCHOOL-ZONE`, `ka-01-ab 1234` → `KA01AB1234`).
- The config is checked at startup. Every problem (duplicate version, dates out of order, no 0 tier, decreasing fines, bad or mixed currencies, too many decimals, bad time zone, a clock skew above the database's 5-minute check…) is collected and the app refuses to start with one message listing them all.

## API

```bash
curl -i -X POST http://localhost:8080/api/readings \
  -H "Content-Type: application/json" \
  -d '{"vehicleId": "KA 01 AB 1234", "zone": "school zone", "speedKph": 55}'
```

`201 Created`, `Location: /api/citations/1`:

```json
{
  "outcome": "VIOLATION",
  "vehicleId": "KA01AB1234",
  "zone": "SCHOOL-ZONE",
  "speedKph": 55.00,
  "speedLimitKph": 25.00,
  "excessKph": 30.00,
  "currency": "INR",
  "defaultLimit": false,
  "citation": {
    "id": 1,
    "vehicleId": "KA01AB1234",
    "zone": "SCHOOL-ZONE",
    "speedKph": 55.00,
    "speedLimitKph": 25.00,
    "excessKph": 30.00,
    "fineAmount": 3000.00,
    "currency": "INR",
    "ruleSetVersion": "2026-10",
    "observedAt": "2026-10-05T19:02:00.367603Z",
    "recordedAt": "2026-10-05T19:02:00.367603Z",
    "reason": "2026-10: SCHOOL-ZONE limit 25 km/h; 55 km/h is +30 over, above the +20 tier, fine ₹3,000"
  },
  "reason": "2026-10: SCHOOL-ZONE limit 25 km/h; 55 km/h is +30 over, above the +20 tier, fine ₹3,000"
}
```

`WITHIN_LIMIT` and `EXEMPT` return `200` with `"citation": null` and a reason such as `2026-09: SCHOOL-ZONE limit 30 km/h; 28 km/h is at or under the limit`.

- `GET /api/citations?limit=1..200&zone=…&before=<id>`: newest first, keyset pagination. A full page has a `Link: <…&before=…>; rel="next"` header.
- `GET /api/citations/{id}`: one citation with its reason. The reason is rebuilt from the stored row, never from the current config, so it matches the POST.
- `GET /api/analytics/summary`: citation count and fine total per zone. All rule sets must use the same currency (INR here), so the totals are in one currency.
- `GET /actuator/health/readiness`: readiness probe (includes the database).

Errors are JSON ProblemDetail bodies: `400` for bad input (with a field list), `404` for an unknown citation or path, `422` for a reading that can't be judged (more than 90 days old or more than 5 minutes in the future).

## Design

```text
src/main/java/org/krish/traffic/
  TrafficRuleEngineApplication.java
  config/    RuleSetProperties (records)  RuleSetValidator  RulesConfig (YAML -> rules)
  rules/     SpeedRuleEngine  RuleSet  RuleSetCatalog  Evaluation  FineTier  Outcome
             Normalizer  Reasons  UnenforceableReadingException
  citation/  Citation  CitationRepository  CitationService  ZoneCount
  web/       ReadingController  CitationController  ApiExceptionHandler  dto/  mapper/
src/main/resources/db/migration/   V1__baseline.sql  V2__citations_exact_types.sql  V3__rule_set_versioning.sql
src/main/resources/static/         the dashboard (plain HTML, CSS, JS)
```

- **Spring-free engine.** `rules/` has no Spring, JPA or database imports. The catalog is a `TreeMap` keyed by start instant, and `floorEntry(observedAt)` picks the rule set.
- **Exact decimals.** Speeds, limits and fines are `BigDecimal` compared with `compareTo`, stored as `numeric(5,2)` and `numeric(12,2)`.
- **The database checks too.** CHECK constraints reject a row where the speed is not over the limit, the excess doesn't equal speed − limit, the tier doesn't fit the excess, or the vehicle id isn't canonical.
- **Flyway V1–V3.** V1 is the old Hibernate-generated table, V2 converts doubles to exact numerics (and aborts if a stored speed has more than 2 decimals), V3 adds the rule-set columns and backfills old rows (rows whose fine doesn't match the old tiers are marked `legacy-unverified`).
- **Citations are immutable** (`@Immutable`, no update paths). Published rule versions are append-only by convention: to change a rule, add a new version with a later `effective-from`.

## Run

Requirements: Java 21 and Docker.

```bash
./scripts/start.sh           # Postgres in Docker, then the app on http://localhost:8080
PORT=8090 DB_PORT=5440 ./scripts/start.sh
docker compose down -v       # delete the local database
```

The default database password in `application.yml` and `docker-compose.yml` is only for the local docker-compose database.

## Tests

```bash
./mvnw verify                # unit tests + integration tests (needs Docker)
./mvnw -DskipITs verify      # unit tests only
```

- Unit tests (35): the engine (date boundaries, strict tier edges, reasons, default limit), the config validator (one test per rule plus startup failure), and that POST and GET give the same reason.
- Integration tests (21, Testcontainers PostgreSQL): the HTTP API end to end, and `MigrationIT`, which runs the migrations over legacy rows, checks the backfill and the `NOT VALID` vehicle check, and checks that V2 aborts and rolls back on a speed like `50.00000000000001`.

## Deploy (Render)

- Health check path: `/actuator/health/readiness`.
- Database settings come from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD`. `SPRING_DATASOURCE_URL`, if set, overrides them, so make sure it isn't left over from an old setup. `PORT` sets the HTTP port.
- Flyway runs at startup. On an empty database it creates the schema from V1–V3.

A database created by the old Hibernate version (a `violations` table, no `flyway_schema_history`) needs a one-time step. Without it, startup stops with "Found non-empty schema(s) … but no schema history table" and nothing is changed. Before the deploy:

1. Back up the database (`pg_dump`). If the data is disposable, recreating the database is simpler.
2. Find speeds that V2 would reject:

   ```sql
   SELECT id, speed_kph, speed_limit_kph FROM violations
   WHERE speed_kph::text::numeric <> round(speed_kph::text::numeric, 2)
      OR speed_limit_kph::text::numeric <> round(speed_limit_kph::text::numeric, 2)
      OR speed_kph > 999.99;
   ```

   Round them to 2 decimals, or delete them. Delete a row that would round down to its limit (for example 30.004 in a 30 zone), because V2 also checks that every stored speed is over the limit.
3. Deploy once with `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`. Flyway records the old table as V1 and runs V2 and V3. Then remove the variable.

If V2 or V3 fails, Postgres rolls that migration back and the app does not start. The baseline row is already written, so the next deploy doesn't need the variable again.

## Limitations

- No authentication, no appeals, no camera or ANPR input.
- Rule values are illustrative, not taken from a real law.
- Fines depend only on how far over the limit, not on the vehicle type.

## License

[MIT](LICENSE)
