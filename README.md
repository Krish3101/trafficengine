# Traffic Violation System

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)
![JUnit 5](https://img.shields.io/badge/Tests-JUnit%205-25A162?logo=junit5&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)

Traffic Violation System is a Spring Boot 3 web application and REST API that detects vehicle speeding violations and calculates tiered penalties based on configurable thresholds. Designed with a dual-entry architecture, it offers a server-rendered Thymeleaf dashboard for manual incident logging and live analytics (total infractions, fine totals, zone-wise breakdowns) alongside a validated JSON REST API for programmatic event ingestion and query operations.

---

## 🌐 Live Demo

The application is deployed live on Render:
- **Web Dashboard**: [https://traffic-app-oxw2.onrender.com](https://traffic-app-oxw2.onrender.com)
- **REST API Endpoint**: [https://traffic-app-oxw2.onrender.com/api/v1/violations](https://traffic-app-oxw2.onrender.com/api/v1/violations)

---

## 🏗️ Architecture Overview

The application follows a clean layered Spring MVC architecture:

- **Presentation Layer**: Dual entry point comprising:
  - `TrafficController`: Handles server-rendered web UI requests via Thymeleaf (`GET /`, `POST /process`).
  - `ViolationRestController`: Serves programmatic JSON endpoints under `/api/v1/violations`.
  - Both controllers validate incoming payloads through Jakarta Bean Validation (`@Valid`) and delegate business logic to the service layer.
- **Service & Domain Layer**:
  - `ViolationEvaluator`: Core rule engine that evaluates `VehicleEvent` instances against configured thresholds. It exempts authorized emergency vehicles, calculates fines according to speed tiers, and persists infractions.
  - `TrafficRulesProperties`: Strongly-typed configuration class bound via `@ConfigurationProperties(prefix = "traffic.rules")`, externalizing rule parameters to `application.yml`.
- **Data Access Layer**:
  - `TrafficViolationRepository`: Spring Data JPA repository extending `JpaRepository` with custom JPQL aggregations for total fine calculation (`sumAllFines`) and zone-based infraction counts (`countViolationsByZone`).
  - `TrafficViolation`: JPA entity mapping violation records with database identity generation and creation timestamps via `@PrePersist`.
- **Persistence Layer**: Default in-memory H2 database (with PostgreSQL compatibility mode) for local execution and automated testing; PostgreSQL driver included at runtime for cloud deployments.

---

## 🛠️ Tech Stack & Decisions

| Component | Technology | Rationale |
|---|---|---|
| **Language & Framework** | Java 21 & Spring Boot 3.4.5 | Modern LTS Java features paired with Spring Boot's dependency management, production-ready ecosystem, and fast startup. |
| **Presentation** | Thymeleaf + Semantic HTML/CSS/JS | Server-side rendering eliminates the complexity and build step of frontend frameworks while keeping UI reactive and accessible. |
| **Data Persistence** | Spring Data JPA (Hibernate 6) | Reduces boilerplate data access code and provides seamless dialect abstraction across H2 and PostgreSQL. |
| **Default Database** | H2 Database (In-Memory) | Zero-setup local development and isolated, fast test execution without external database installation. |
| **Production Database** | PostgreSQL Driver (`runtime`) | Production-grade transactional database support for cloud deployments (Render). |
| **Data Validation** | Jakarta Bean Validation | Declarative constraint annotations directly on data transfer objects (`ViolationForm`) to ensure boundary validation before reaching business logic. |
| **Testing** | JUnit 5, Spring Boot Test, Mockito | Comprehensive test coverage spanning slice unit tests (`ViolationEvaluatorTest`) and full integration tests (`TrafficApplicationTests`) via MockMvc. |
| **Code Formatting** | Spotless (Google Java Format) | Enforces standard Java formatting rules and automatically removes unused imports during build verification. |
| **Containerization** | Docker & Docker Compose | Multi-stage build containerization producing minimal JRE runtime images for consistent local and production deployment. |

---

## ⚙️ Configuration & Environment Variables

The application ships with sensible local defaults in `src/main/resources/application.yml`. No `.env` file is required to run out of the box with the default in-memory database.

To customize runtime settings or configure a production database, copy the template:
```bash
cp .env.example .env
```

| Environment Variable | Description | Default / Fallback |
|---|---|---|
| `PORT` | HTTP port for the web server | `8080` |
| `SPRING_DATASOURCE_URL` | JDBC URL for database connection | In-memory H2 database (`jdbc:h2:mem:trafficdb;...`) |
| `DB_USER` | Database user credential | `sa` |
| `DB_PASSWORD` | Database password credential | *(empty)* |
| `SHOW_SQL` | Log executed SQL queries to console | `false` |

Rule thresholds and fine tiers are configured in `application.yml` under `traffic.rules`:
```yaml
traffic:
  rules:
    speed-threshold: 80.0
    fine-tiers:
      - threshold: 120.0
        amount: 5000
      - threshold: 100.0
        amount: 2000
      - threshold: 80.0
        amount: 1000
```

---

## 🚀 How to Run Locally

### Prerequisites
- **Java 21** or later (`java -version`)
- **Maven** (optional — the included `./mvnw` wrapper will self-bootstrap Maven)
- **Docker** *(optional, for containerized run)*

### Step 1: Clone the Repository
```bash
git clone <repo-url>
cd traffic-app
```

### Step 2: (Optional) Set Up Environment File
If connecting to an external database (e.g., PostgreSQL):
```bash
cp .env.example .env
```
Edit `.env` with your database credentials. For standard in-memory development, skip this step.

### Step 3: Code Formatting & Automated Tests
Check code formatting compliance:
```bash
./mvnw spotless:check
```

Optionally auto-apply formatting:
```bash
./mvnw spotless:apply
```

Execute the unit and integration tests:
```bash
./mvnw test
```

### Step 4: Run the Application
Start the application with the Spring Boot Maven plugin:
```bash
./mvnw spring-boot:run
```

Alternatively, build and execute the packaged JAR:
```bash
./mvnw clean package -DskipTests
java -jar target/traffic-0.0.1-SNAPSHOT.jar
```

Or run via Docker Compose:
```bash
docker compose up --build
```

### Step 5: Access the Application
- **Web Dashboard**: [http://localhost:8080/](http://localhost:8080/)
- **REST API Endpoint**: [http://localhost:8080/api/v1/violations](http://localhost:8080/api/v1/violations)

---

## 📡 REST API Reference

Base path: `/api/v1/violations`

### 1. List All Violations
```bash
curl -X GET http://localhost:8080/api/v1/violations
```
**Response (200 OK)**:
```json
[
  {
    "id": 1,
    "vehicleId": "KA03MM1234",
    "speed": 110.0,
    "zone": "Zone-B",
    "fine": 2000,
    "createdAt": "2026-06-25T14:30:00"
  }
]
```

### 2. Submit Vehicle Event (Violation Detected)
```bash
curl -X POST http://localhost:8080/api/v1/violations \
  -H "Content-Type: application/json" \
  -d '{
    "vehicleId": "KA03MM1234",
    "speed": 110.0,
    "zone": "Zone-B",
    "emergency": false
  }'
```
**Response (201 Created)**:
```json
{
  "violationDetected": true,
  "message": "Violation saved successfully",
  "violation": {
    "id": 1,
    "vehicleId": "KA03MM1234",
    "speed": 110.0,
    "zone": "Zone-B",
    "fine": 2000,
    "createdAt": "2026-06-25T14:30:00"
  }
}
```

### 3. Submit Vehicle Event (No Violation / Emergency Vehicle)
```bash
curl -X POST http://localhost:8080/api/v1/violations \
  -H "Content-Type: application/json" \
  -d '{
    "vehicleId": "AMB-01",
    "speed": 130.0,
    "zone": "Zone-A",
    "emergency": true
  }'
```
**Response (200 OK)**:
```json
{
  "violationDetected": false,
  "message": "No violation detected for this event"
}
```

### 4. Input Validation Error
```bash
curl -X POST http://localhost:8080/api/v1/violations \
  -H "Content-Type: application/json" \
  -d '{
    "vehicleId": "A",
    "speed": 350.0,
    "zone": "Zone-?",
    "emergency": false
  }'
```
**Response (400 Bad Request)**:
```json
{
  "errors": [
    "Zone must contain only alphanumeric characters, spaces, hyphens, or underscores",
    "Speed cannot exceed 300 km/h",
    "Vehicle ID must be between 2 and 20 characters"
  ]
}
```

---

## 🔮 Future Scope

The following capabilities were intentionally left out or simplified as conscious scoping decisions:

- **Synchronous REST vs. Message Queues (Kafka / RabbitMQ)**: The system processes events synchronously via HTTP endpoints. Dedicated message brokers and queues were deferred to keep the single-node deployment lightweight and maintainable, as current request volume does not require asynchronous decoupling or message buffering.
- **Externalized File Configuration vs. Dynamic Database Rules**: Speed thresholds and fine tiers are configured via `application.yml` rather than a dynamic admin dashboard. This keeps rule definition version-controlled, auditable, and immutable at runtime without needing complex administrative authentication.
- **Hibernate Schema Generation vs. Database Migrations**: Hibernate's `ddl-auto: update` is used for schema creation to minimize setup complexity for demo environments. Migration tooling (Flyway or Liquibase) was scoped out until multi-environment database lifecycles require strict versioned change sets.
- **Open Dashboard vs. Role-Based Access Control (RBAC)**: Authentication and authorization (e.g., Spring Security / OAuth2) were scoped out so reviewers and users can interact with the evaluation UI and query analytics without barrier friction.
