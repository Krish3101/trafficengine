# Smart Traffic System

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)
![JUnit 5](https://img.shields.io/badge/Tests-JUnit%205-25A162?logo=junit5&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

A **Spring Boot 3** application featuring a Thymeleaf web UI and a JSON REST API that detects traffic speed violations and automatically calculates fines using a configurable tier system. Demonstrates layered Spring MVC architecture, Jakarta Bean Validation, JPA persistence, and property-driven business rule configuration.

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3, Spring MVC |
| Templating | Thymeleaf |
| Persistence | Spring Data JPA + H2 (in-memory, PostgreSQL-compatible mode) |
| Validation | Jakarta Bean Validation |
| Build | Maven Wrapper (`./mvnw`) |
| Testing | JUnit 5, Spring Boot Test, MockMvc |
| Containerisation | Docker + Docker Compose |

---

## 🚀 Features

- **Speed Violation Detection**: Processes vehicle speed events and flags non-emergency vehicles exceeding the configured threshold.
- **Dynamic Fine Calculator**: Automatically maps violations to distinct fine tiers configured via application properties.
- **Dual Interface**:
  - **Thymeleaf UI**: MVC-based form to submit violations manually, inspect saved logs, and view aggregate analytical statistics.
  - **REST API**: JSON endpoints under `/api/v1/violations` to programmatically submit vehicle events and retrieve violations.
- **Robust Input Validation**: Strict validation on vehicle IDs, speeds, and zones using Jakarta Bean Validation.

---

## 📋 Prerequisites

- **Java 21** or higher
- **Maven** (packaged via the included Maven Wrapper `./mvnw`)

---

## 🛠️ Setup & Execution

### 1) Run the Application
Start the Spring Boot application locally:
```bash
./mvnw spring-boot:run
```
The server will start on port **8080** by default. You can open **http://localhost:8080** in your browser to access the web UI.

### 2) Run Tests
Run the JUnit 5 test suite to verify controller bindings, validations, rules loading, and DB operations:
```bash
./mvnw test
```

### 3) Package the Application
Build the executable fat JAR file:
```bash
./mvnw clean package
```
The compiled jar file will be saved at `target/traffic-0.0.1-SNAPSHOT.jar`.

---

## 🛡️ Spring Boot Jakarta Form Validation

Input validation is enforced using Jakarta Bean Validation annotations on the input data model (`ViolationForm.java`):

```java
public class ViolationForm {
    @NotBlank(message = "Vehicle ID is required")
    @Size(min = 2, max = 20, message = "Vehicle ID must be between 2 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9- ]+$", message = "Vehicle ID must contain only alphanumeric characters, spaces, or hyphens")
    private String vehicleId;

    @NotNull(message = "Speed is required")
    @DecimalMin(value = "0.0", message = "Speed must be positive")
    @DecimalMax(value = "300.0", message = "Speed cannot exceed 300 km/h")
    private Double speed;

    @NotBlank(message = "Zone is required")
    @Size(min = 2, max = 50, message = "Zone must be between 2 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9-_ ]+$", message = "Zone must contain only alphanumeric characters, spaces, hyphens, or underscores")
    private String zone;

    private boolean emergency;
    
    // Getters and Setters...
}
```

Validation is triggered in both the MVC controller and the REST controller:
- **Thymeleaf MVC Controller (`TrafficController.java`)**:
  ```java
  @PostMapping("/process")
  public String process(
          @Valid @ModelAttribute("form") ViolationForm form,
          BindingResult bindingResult,
          Model model) {
      if (bindingResult.hasErrors()) {
          // Extracts errors and returns validation status to Thymeleaf
          ...
          return "index";
      }
      ...
  }
  ```
- **REST Controller (`ViolationRestController.java`)**:
  ```java
  @PostMapping
  public ResponseEntity<?> submitEvent(@Valid @RequestBody ViolationForm form, BindingResult bindingResult) {
      if (bindingResult.hasErrors()) {
          List<String> errors = bindingResult.getAllErrors().stream()
                  .map(error -> error.getDefaultMessage())
                  .collect(Collectors.toList());
          return ResponseEntity.badRequest().body(Map.of("errors", errors));
      }
      ...
  }
  ```

---

## ⚙️ Custom Threshold Properties in `application.yml`

The speed thresholds and fine amounts are fully configurable inside `src/main/resources/application.yml` under the custom prefix `traffic.rules`:

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

These configuration settings are bound at runtime into a strongly-typed Spring bean using `@ConfigurationProperties` (`TrafficRulesProperties.java`):

```java
@Component
@ConfigurationProperties(prefix = "traffic.rules")
public class TrafficRulesProperties {
    private double speedThreshold;
    private List<FineTier> fineTiers;

    // Inner static class FineTier mapping individual tier entries
    public static class FineTier {
        private double threshold;
        private int amount;
        ...
    }
    ...
}
```

---

## 🌐 Executing the REST API Violation Endpoints

The API base URL is: `http://localhost:8080/api/v1/violations`

### 1) Get All Saved Violations
Retrieve all traffic violations stored in the H2 database.
- **Request**:
  ```bash
  curl -X GET http://localhost:8080/api/v1/violations
  ```
- **Response (200 OK)**:
  ```json
  [
    {
      "id": 1,
      "vehicleId": "KA03MM1234",
      "speed": 110.0,
      "zone": "Zone-B",
      "fine": 2000,
      "createdAt": "2026-06-25T14:30:00.000000"
    }
  ]
  ```

### 2) Submit Vehicle Speed Event (No Violation)
Submit a vehicle event where the speed is within the speed limit (e.g. 70 km/h) or the vehicle is an emergency vehicle.
- **Request**:
  ```bash
  curl -X POST http://localhost:8080/api/v1/violations \
    -H "Content-Type: application/json" \
    -d '{"vehicleId": "MH12AB1234", "speed": 75.0, "zone": "Zone-A", "emergency": false}'
  ```
- **Response (200 OK)**:
  ```json
  {
    "violationDetected": false,
    "message": "No violation detected for this event"
  }
  ```

### 3) Submit Vehicle Speed Event (Violation Detected)
Submit a vehicle event that triggers a speeding violation (speed > 80.0 and `emergency` is false).
- **Request**:
  ```bash
  curl -X POST http://localhost:8080/api/v1/violations \
    -H "Content-Type: application/json" \
    -d '{"vehicleId": "KA03MM1234", "speed": 110.0, "zone": "Zone-B", "emergency": false}'
  ```
- **Response (201 Created)**:
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
      "createdAt": "2026-06-25T14:30:00.000000"
    }
  }
  ```

### 4) Submit Invalid Payload (Validation Failure)
Submit an invalid vehicle event that fails Jakarta Bean Validation constraints (e.g., missing speed, blank vehicle ID, special characters in zone name).
- **Request**:
  ```bash
  curl -X POST http://localhost:8080/api/v1/violations \
    -H "Content-Type: application/json" \
    -d '{"vehicleId": "", "speed": 350.0, "zone": "Zone-?", "emergency": false}'
  ```
- **Response (400 Bad Request)**:
  ```json
  {
    "errors": [
      "Vehicle ID is required",
      "Speed cannot exceed 300 km/h",
      "Zone must contain only alphanumeric characters, spaces, hyphens, or underscores"
    ]
  }
  ```
