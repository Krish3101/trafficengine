# Traffic Violation System

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)

A lightweight, deterministic rule engine for adjudicating traffic speeding violations. Built with **Java 21** and **Spring Boot 3**, this system accepts vehicle events and applies configurable business rules to determine if a violation occurred and what penalty applies.

## 🎯 Core Features

- **Strict Rule Engine**: Evaluates speeds against configurable thresholds.
- **Emergency Exemption**: Vehicles flagged as emergency units are strictly exempted prior to any penalty evaluation.
- **Dual Entry Points**: 
  - **REST API**: For programmatic machine-to-machine integrations.
  - **Web Dashboard**: Server-rendered Thymeleaf dashboard for manual logging and analytics.
- **Zero-State Configuration**: Thresholds and fine tiers are configured via `application.yml` rather than a dynamic admin dashboard. This keeps the enforcement policy version-controlled, auditable, and immutable at runtime.
- **Graceful Degradation**: Missing properties trigger fallback behaviors while invalid payloads are deterministically rejected.

## 🏗️ Tech Stack

- **Framework**: Java 21, Spring Boot 3 (Web, Data JPA, Validation)
- **UI**: Thymeleaf + Semantic HTML/CSS
- **Database**: H2 (In-Memory default for local/tests) / PostgreSQL (Production)
- **Testing**: JUnit 5, MockMvc

## 🚀 How to Run Locally

### Prerequisites
- **Java 21** or later

### Build and Run
1. **Clone the repo**
   ```bash
   git clone https://github.com/Krish3101/traffic-app.git
   cd traffic-app
   ```
2. **Run Tests & Format Checks**
   ```bash
   ./mvnw clean test
   ./mvnw spotless:check
   ```
3. **Start the Application**
   ```bash
   ./mvnw spring-boot:run
   ```
   The application will start on port `8080` with an in-memory database. No `.env` is required.

### Access
- **Dashboard**: [http://localhost:8080](http://localhost:8080)
- **API Endpoint**: `http://localhost:8080/api/v1/violations`

---

## 📡 REST API Reference

Base path: `/api/v1/violations`

### 1. Submit Vehicle Event (Violation Detected)
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
  "id": 1,
  "vehicleId": "KA03MM1234",
  "speed": 110.0,
  "zone": "Zone-B",
  "fine": 2000,
  "createdAt": "2026-09-08T14:30:00"
}
```

### 2. Submit Vehicle Event (No Violation / Exempted)
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
```text
No violation detected
```

### 3. Input Validation Error
```bash
curl -X POST http://localhost:8080/api/v1/violations \
  -H "Content-Type: application/json" \
  -d '{"vehicleId": "A", "speed": 350.0, "zone": "Zone-?"}'
```
**Response (400 Bad Request)**:
```json
{
  "errors": [
    "Speed cannot exceed 300 km/h",
    "Vehicle ID must be between 2 and 20 characters",
    "Zone must contain only alphanumeric characters, spaces, hyphens, or underscores"
  ]
}
```

## 💡 Design Decisions (YAGNI Principle)

This project is intentionally designed to avoid over-engineering:
- **No Complex DTOs**: The application operates directly on simple web forms and outputs entities where applicable, avoiding bloated mapping layers.
- **Synchronous Processing**: HTTP endpoints are fully synchronous; no message brokers (Kafka/RabbitMQ) were added since the current requirements do not warrant asynchronous event buffering.
- **No RBAC**: Authentication is omitted by design to remove setup friction.
