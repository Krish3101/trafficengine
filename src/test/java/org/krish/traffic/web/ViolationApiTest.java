package org.krish.traffic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.krish.traffic.violation.Violation;
import org.krish.traffic.violation.ViolationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViolationApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ViolationRepository violationRepository;

  @BeforeEach
  void cleanDatabase() {
    violationRepository.deleteAll();
  }

  @Test
  @DisplayName(
      "POST 110 km/h, non-emergency, Zone-B -> 201, outcome: VIOLATION, fineAmount: 2000, one row added")
  void postViolationRecorded() throws Exception {
    String payload =
        """
        {
          "vehicleId": "KA03MM1234",
          "zone": "Zone-B",
          "speedKph": 110.0,
          "emergency": false
        }
        """;

    mockMvc
        .perform(post("/api/readings").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.outcome", is("VIOLATION")))
        .andExpect(jsonPath("$.vehicleId", is("KA03MM1234")))
        .andExpect(jsonPath("$.zone", is("ZONE-B")))
        .andExpect(jsonPath("$.speedKph", is(110.0)))
        .andExpect(jsonPath("$.speedLimitKph", is(80.0)))
        .andExpect(jsonPath("$.excessKph", is(30.0)))
        .andExpect(jsonPath("$.currency", is("INR")))
        .andExpect(jsonPath("$.violation.fineAmount", is(2000)))
        .andExpect(jsonPath("$.violation.vehicleId", is("KA03MM1234")))
        .andExpect(jsonPath("$.violation.zone", is("ZONE-B")));

    assertThat(violationRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "POST 150 km/h, emergency -> 200, outcome: EXEMPT, violation: null, no row added")
  void postEmergencyExempt() throws Exception {
    String payload =
        """
        {
          "vehicleId": "AMB-01",
          "zone": "Zone-A",
          "speedKph": 150.0,
          "emergency": true
        }
        """;

    mockMvc
        .perform(post("/api/readings").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("EXEMPT")))
        .andExpect(jsonPath("$.vehicleId", is("AMB-01")))
        .andExpect(jsonPath("$.zone", is("ZONE-A")))
        .andExpect(jsonPath("$.speedKph", is(150.0)))
        .andExpect(jsonPath("$.speedLimitKph", is(80.0)))
        .andExpect(jsonPath("$.excessKph", is(70.0)))
        .andExpect(jsonPath("$.violation", nullValue()));

    assertThat(violationRepository.count()).isZero();
  }

  @Test
  @DisplayName("POST exactly 80.0 -> 200, outcome: WITHIN_LIMIT, no row added")
  void postWithinLimit() throws Exception {
    String payload =
        """
        {
          "vehicleId": "KA01AB1111",
          "zone": "Zone-A",
          "speedKph": 80.0,
          "emergency": false
        }
        """;

    mockMvc
        .perform(post("/api/readings").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("WITHIN_LIMIT")))
        .andExpect(jsonPath("$.excessKph", is(0.0)))
        .andExpect(jsonPath("$.violation", nullValue()));

    assertThat(violationRepository.count()).isZero();
  }

  @Test
  @DisplayName("POST speedKph: 300.1 -> 400 with the speed message in errors")
  void postSpeedExcessExceedsConstraint() throws Exception {
    String payload =
        """
        {
          "vehicleId": "KA01AB1111",
          "zone": "Zone-A",
          "speedKph": 300.1,
          "emergency": false
        }
        """;

    mockMvc
        .perform(post("/api/readings").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status", is(400)))
        .andExpect(jsonPath("$.title", is("Bad Request")))
        .andExpect(jsonPath("$.detail", is("Request validation failed")))
        .andExpect(jsonPath("$.errors[0].field", is("speedKph")))
        .andExpect(jsonPath("$.errors[0].message", is("Speed must be between 0 and 300 km/h")));
  }

  @Test
  @DisplayName(
      "POST blank vehicleId, -1.0, zone Zone-? -> 400, all three field errors, sorted by field")
  void postMultipleBrokenConstraints() throws Exception {
    String payload =
        """
        {
          "vehicleId": "   ",
          "zone": "Zone-?",
          "speedKph": -1.0,
          "emergency": false
        }
        """;

    mockMvc
        .perform(post("/api/readings").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(3)))
        .andExpect(jsonPath("$.errors[0].field", is("speedKph")))
        .andExpect(jsonPath("$.errors[1].field", is("vehicleId")))
        .andExpect(jsonPath("$.errors[2].field", is("zone")));
  }

  @Test
  @DisplayName("POST malformed JSON -> 400 JSON, not a 500")
  void postMalformedJsonReturns400() throws Exception {
    String malformedPayload = "{ not valid json ";

    mockMvc
        .perform(
            post("/api/readings").contentType(MediaType.APPLICATION_JSON).content(malformedPayload))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", containsString("application/json")))
        .andExpect(jsonPath("$.status", is(400)))
        .andExpect(jsonPath("$.title", is("Bad Request")));
  }

  @Test
  @DisplayName("POST zone ' zone-a ' -> Stored and returned as ZONE-A")
  void postZoneNormalized() throws Exception {
    String payload =
        """
        {
          "vehicleId": "ka-01-ab-1234",
          "zone": " zone-a ",
          "speedKph": 110.0,
          "emergency": false
        }
        """;

    mockMvc
        .perform(post("/api/readings").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.zone", is("ZONE-A")))
        .andExpect(jsonPath("$.vehicleId", is("KA-01-AB-1234")))
        .andExpect(jsonPath("$.violation.zone", is("ZONE-A")))
        .andExpect(jsonPath("$.violation.vehicleId", is("KA-01-AB-1234")));

    Violation saved = violationRepository.findAll().getFirst();
    assertThat(saved.getZone()).isEqualTo("ZONE-A");
    assertThat(saved.getVehicleId()).isEqualTo("KA-01-AB-1234");
  }

  @Test
  @DisplayName("GET /api/violations -> 200, at most 50 items, newest first")
  void getRecentViolationsDefaultLimit() throws Exception {
    Instant base = Instant.parse("2026-09-10T10:00:00Z");
    for (int i = 1; i <= 60; i++) {
      violationRepository.save(
          new Violation(null, "VEH-" + i, "ZONE-A", 100.0, 80.0, 1000, base.plusSeconds(i)));
    }

    mockMvc
        .perform(get("/api/violations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(50)))
        .andExpect(jsonPath("$[0].vehicleId", is("VEH-60")))
        .andExpect(jsonPath("$[49].vehicleId", is("VEH-11")));
  }

  @Test
  @DisplayName("GET /api/violations?limit=5000 -> 200, at most 200 items")
  void getRecentViolationsCappedAt200() throws Exception {
    Instant base = Instant.parse("2026-09-10T10:00:00Z");
    for (int i = 1; i <= 210; i++) {
      violationRepository.save(
          new Violation(null, "VEH-" + i, "ZONE-A", 100.0, 80.0, 1000, base.plusSeconds(i)));
    }

    mockMvc
        .perform(get("/api/violations?limit=5000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(200)))
        .andExpect(jsonPath("$[0].vehicleId", is("VEH-210")));
  }

  @Test
  @DisplayName("GET /api/violations?limit=abc -> 400 naming the field, not a 500")
  void getViolationsRejectsNonNumericLimit() throws Exception {
    mockMvc
        .perform(get("/api/violations?limit=abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field", is("limit")));
  }

  @Test
  @DisplayName("GET /api/violations?zone=zone-a -> Only ZONE-A rows")
  void getViolationsFilteredByZoneCaseInsensitive() throws Exception {
    Instant now = Instant.now();
    violationRepository.save(new Violation(null, "VEH-1", "ZONE-A", 100.0, 80.0, 1000, now));
    violationRepository.save(new Violation(null, "VEH-2", "ZONE-B", 100.0, 80.0, 1000, now));
    violationRepository.save(new Violation(null, "VEH-3", "ZONE-A", 110.0, 80.0, 2000, now));

    mockMvc
        .perform(get("/api/violations?zone=zone-a"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].zone", is("ZONE-A")))
        .andExpect(jsonPath("$[1].zone", is("ZONE-A")));
  }

  @Test
  @DisplayName("GET /api/analytics/summary after known readings -> Totals match exactly")
  void analyticsSummaryWithKnownReadings() throws Exception {
    Instant now = Instant.now();
    violationRepository.save(new Violation(null, "V-1", "ZONE-A", 100.0, 80.0, 1000, now));
    violationRepository.save(new Violation(null, "V-2", "ZONE-A", 100.0, 80.0, 1000, now));
    violationRepository.save(new Violation(null, "V-3", "ZONE-B", 120.0, 80.0, 2000, now));

    mockMvc
        .perform(get("/api/analytics/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalViolations", is(3)))
        .andExpect(jsonPath("$.totalFineAmount", is(4000)))
        .andExpect(jsonPath("$.currency", is("INR")))
        .andExpect(jsonPath("$.zones", hasSize(2)))
        .andExpect(jsonPath("$.zones[0].zone", is("ZONE-A")))
        .andExpect(jsonPath("$.zones[0].violations", is(2)))
        .andExpect(jsonPath("$.zones[0].fineAmount", is(2000)))
        .andExpect(jsonPath("$.zones[1].zone", is("ZONE-B")))
        .andExpect(jsonPath("$.zones[1].violations", is(1)))
        .andExpect(jsonPath("$.zones[1].fineAmount", is(2000)));
  }

  @Test
  @DisplayName(
      "GET /api/analytics/summary on an empty ledger -> 200, zeros and empty zones array")
  void analyticsSummaryEmptyLedger() throws Exception {
    mockMvc
        .perform(get("/api/analytics/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalViolations", is(0)))
        .andExpect(jsonPath("$.totalFineAmount", is(0)))
        .andExpect(jsonPath("$.currency", is("INR")))
        .andExpect(jsonPath("$.zones", hasSize(0)));
  }

  @Test
  @DisplayName("GET / -> 200 text/html")
  void rootEndpointServesHtml() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  @DisplayName("Every response in the suite -> JSON; never text/plain")
  void apiEndpointsAlwaysReturnJson() throws Exception {
    mockMvc
        .perform(get("/api/analytics/summary"))
        .andExpect(header().string("Content-Type", containsString("application/json")));

    mockMvc
        .perform(get("/api/violations"))
        .andExpect(header().string("Content-Type", containsString("application/json")));

    mockMvc
        .perform(post("/api/readings").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(header().string("Content-Type", containsString("application/json")));
  }
}
