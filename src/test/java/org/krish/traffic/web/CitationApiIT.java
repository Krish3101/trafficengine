package org.krish.traffic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.krish.traffic.AbstractIntegrationTest;
import org.krish.traffic.citation.Citation;
import org.krish.traffic.citation.CitationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class CitationApiIT extends AbstractIntegrationTest {

  // nanosecond precision on purpose: Linux clocks have it, Postgres keeps only micros
  static final Instant NOW = Instant.parse("2026-10-05T10:15:08.093377593Z");

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CitationRepository citationRepository;

  @BeforeEach
  void cleanDatabase() {
    citationRepository.deleteAll();
  }

  private ResultActions postReading(String json) throws Exception {
    return mockMvc.perform(
        post("/api/readings").contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private Citation saveRow(String vehicleId, String zone, String speed, String fine) {
    BigDecimal s = new BigDecimal(speed);
    BigDecimal limit = new BigDecimal("80.00");
    return citationRepository.save(
        new Citation(
            null, vehicleId, zone, s, limit, s.subtract(limit), new BigDecimal("0.00"),
            new BigDecimal(fine), "INR", "2026-09", NOW.minusSeconds(60), NOW.minusSeconds(60)));
  }

  @Test
  @DisplayName("POST 130 on HIGHWAY-1 -> 201 + Location, VIOLATION under 2026-10, fine 3000")
  void postViolationRecorded() throws Exception {
    postReading(
            """
            {"vehicleId": "KA03MM1234", "zone": "highway-1", "speedKph": 130.00, "emergency": false}
            """)
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/citations/")))
        .andExpect(jsonPath("$.outcome", is("VIOLATION")))
        .andExpect(jsonPath("$.vehicleId", is("KA03MM1234")))
        .andExpect(jsonPath("$.zone", is("HIGHWAY-1")))
        .andExpect(jsonPath("$.speedLimitKph", is(100.00)))
        .andExpect(jsonPath("$.excessKph", is(30.00)))
        .andExpect(jsonPath("$.defaultLimit", is(false)))
        .andExpect(jsonPath("$.citation.fineAmount", is(3000.00)))
        .andExpect(jsonPath("$.citation.ruleSetVersion", is("2026-10")))
        .andExpect(jsonPath("$.violation").doesNotExist())
        .andExpect(
            jsonPath(
                "$.reason",
                is("2026-10: HIGHWAY-1 limit 100 km/h; 130 km/h is +30 over, above the +20 tier, fine ₹3,000")));

    assertThat(citationRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("POST then GET return the same citation JSON (observedAt truncated to micros)")
  void postAndGetCitationAreEqual() throws Exception {
    String body =
        postReading(
                """
                {"vehicleId": "KA01AB1234", "zone": "SCHOOL-ZONE", "speedKph": 55}
                """)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode posted = objectMapper.readTree(body).get("citation");
    assertThat(posted.get("observedAt").asText()).isEqualTo("2026-10-05T10:15:08.093377Z");

    String fetched =
        mockMvc
            .perform(get("/api/citations/" + posted.get("id").asLong()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(fetched)).isEqualTo(posted);
    assertThat(objectMapper.readTree(body).get("reason")).isEqualTo(posted.get("reason"));
  }

  @Test
  @DisplayName("The same reading either side of 2026-10-01 (Kolkata midnight) uses two rule sets")
  void readingsEitherSideOfTheBoundary() throws Exception {
    postReading(
            """
            {"vehicleId": "KA01AB1234", "zone": "SCHOOL-ZONE", "speedKph": 28,
             "observedAt": "2026-09-30T23:59:59+05:30"}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("WITHIN_LIMIT")))
        .andExpect(
            jsonPath("$.reason", is("2026-09: SCHOOL-ZONE limit 30 km/h; 28 km/h is at or under the limit")));

    postReading(
            """
            {"vehicleId": "KA01AB1234", "zone": "SCHOOL-ZONE", "speedKph": 28,
             "observedAt": "2026-10-01T00:00:00+05:30"}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.outcome", is("VIOLATION")))
        .andExpect(jsonPath("$.citation.ruleSetVersion", is("2026-10")))
        .andExpect(
            jsonPath(
                "$.reason",
                is("2026-10: SCHOOL-ZONE limit 25 km/h; 28 km/h is +3 over, above the +0 tier, fine ₹1,500")));
  }

  @Test
  @DisplayName("observedAt older than 90 days or more than 5 minutes ahead -> 422")
  void unenforceableTimestamps() throws Exception {
    postReading(
            """
            {"vehicleId": "KA01AB1234", "zone": "SCHOOL-ZONE", "speedKph": 55,
             "observedAt": "2026-07-01T00:00:00Z"}
            """)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status", is(422)));

    postReading(
            """
            {"vehicleId": "KA01AB1234", "zone": "SCHOOL-ZONE", "speedKph": 55,
             "observedAt": "2026-10-05T10:30:00Z"}
            """)
        .andExpect(status().isUnprocessableEntity());

    assertThat(citationRepository.count()).isZero();
  }

  @Test
  @DisplayName("POST 150 km/h, emergency -> 200 EXEMPT, no citation, no row")
  void postEmergencyExempt() throws Exception {
    postReading(
            """
            {"vehicleId": "AMB01", "zone": "HIGHWAY-1", "speedKph": 150.00, "emergency": true}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("EXEMPT")))
        .andExpect(jsonPath("$.excessKph", is(50.00)))
        .andExpect(jsonPath("$.citation", nullValue()))
        .andExpect(jsonPath("$.reason", containsString("emergency vehicle exempt (would be +50 over)")));

    assertThat(citationRepository.count()).isZero();
  }

  @Test
  @DisplayName("POST exactly at the limit -> 200 WITHIN_LIMIT, no row")
  void postWithinLimit() throws Exception {
    postReading(
            """
            {"vehicleId": "KA01AB1111", "zone": "SCHOOL-ZONE", "speedKph": 25.00}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("WITHIN_LIMIT")))
        .andExpect(jsonPath("$.excessKph", is(0.0)))
        .andExpect(jsonPath("$.citation", nullValue()));

    assertThat(citationRepository.count()).isZero();
  }

  @Test
  @DisplayName("Unknown zone -> general limit 80 and defaultLimit true")
  void unknownZoneUsesDefaultLimit() throws Exception {
    postReading(
            """
            {"vehicleId": "KA01AB1111", "zone": "Market St", "speedKph": 90}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.zone", is("MARKET-ST")))
        .andExpect(jsonPath("$.speedLimitKph", is(80.00)))
        .andExpect(jsonPath("$.defaultLimit", is(true)))
        .andExpect(jsonPath("$.reason", containsString("MARKET-ST limit 80 km/h")));
  }

  @Test
  @DisplayName("POST speedKph as a JSON string \"88.15\" -> accepted, stored exactly")
  void postSpeedAsStringStoredExactly() throws Exception {
    postReading(
            """
            {"vehicleId": "KA03MM5678", "zone": "HIGHWAY-1", "speedKph": "88.15", "emergency": false}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("WITHIN_LIMIT")))
        .andExpect(jsonPath("$.speedKph", is(88.15)));

    postReading(
            """
            {"vehicleId": "KA03MM9999", "zone": "SCHOOL-ZONE", "speedKph": "88.15", "emergency": false}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.outcome", is("VIOLATION")))
        .andExpect(jsonPath("$.speedKph", is(88.15)));

    assertThat(citationRepository.findAll())
        .extracting(c -> c.getSpeedKph())
        .containsExactly(new BigDecimal("88.15"));
  }

  @Test
  @DisplayName("POST speedKph 300.01 -> 400 with a speedKph error")
  void postSpeedAboveMaximum() throws Exception {
    postReading(
            """
            {"vehicleId": "KA01AB1111", "zone": "SCHOOL-ZONE", "speedKph": 300.01}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status", is(400)))
        .andExpect(jsonPath("$.title", is("Bad Request")))
        .andExpect(jsonPath("$.detail", is("Request validation failed")))
        .andExpect(jsonPath("$.errors[0].field", is("speedKph")));
  }

  @Test
  @DisplayName("POST blank vehicleId, -1.0, zone Zone-? -> 400 with three field errors")
  void postMultipleBrokenConstraints() throws Exception {
    postReading(
            """
            {"vehicleId": "   ", "zone": "Zone-?", "speedKph": -1.0}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(3)));
  }

  @Test
  @DisplayName("POST malformed JSON -> 400 JSON, not a 500")
  void postMalformedJsonReturns400() throws Exception {
    postReading("{ not valid json ")
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", containsString("application/json")))
        .andExpect(jsonPath("$.status", is(400)));
  }

  @Test
  @DisplayName("POST zone ' school zone ', vehicleId 'ka-01-ab-1234' -> stored canonicalised")
  void postZoneAndVehicleNormalized() throws Exception {
    postReading(
            """
            {"vehicleId": "ka-01-ab-1234", "zone": " school zone ", "speedKph": 40}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.zone", is("SCHOOL-ZONE")))
        .andExpect(jsonPath("$.vehicleId", is("KA01AB1234")))
        .andExpect(jsonPath("$.citation.zone", is("SCHOOL-ZONE")));

    Citation saved = citationRepository.findAll().getFirst();
    assertThat(saved.getZone()).isEqualTo("SCHOOL-ZONE");
    assertThat(saved.getVehicleId()).isEqualTo("KA01AB1234");
  }

  @Test
  @DisplayName("GET /api/citations -> default limit 50, newest first, Link to the next page")
  void getRecentCitationsDefaultLimit() throws Exception {
    for (int i = 1; i <= 60; i++) {
      saveRow("VEH" + i, "HIGHWAY-1", "100.00", "1000.00");
    }

    mockMvc
        .perform(get("/api/citations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(50)))
        .andExpect(jsonPath("$[0].vehicleId", is("VEH60")))
        .andExpect(header().string(HttpHeaders.LINK, containsString("rel=\"next\"")));
  }

  @Test
  @DisplayName("GET /api/citations?limit=200 -> 200 items")
  void getRecentCitationsCappedAt200() throws Exception {
    for (int i = 1; i <= 210; i++) {
      saveRow("VEH" + i, "HIGHWAY-1", "100.00", "1000.00");
    }

    mockMvc
        .perform(get("/api/citations?limit=200"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(200)))
        .andExpect(header().exists(HttpHeaders.LINK));
  }

  @Test
  @DisplayName("GET /api/citations with limit 0, 201 or abc -> 400")
  void getCitationsRejectsOutOfRangeLimit() throws Exception {
    mockMvc.perform(get("/api/citations?limit=0")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/citations?limit=201")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/citations?limit=abc")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/citations?zone=school_zone -> only SCHOOL-ZONE rows")
  void getCitationsFilteredByZone() throws Exception {
    saveRow("VEH1", "SCHOOL-ZONE", "100.00", "1000.00");
    saveRow("VEH2", "HIGHWAY-1", "100.00", "1000.00");

    mockMvc
        .perform(get("/api/citations?zone=school_zone"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].zone", is("SCHOOL-ZONE")));
  }

  @Test
  @DisplayName("GET /api/citations/{id} -> 200 with reason; unknown id -> 404 ProblemDetail")
  void getCitationById() throws Exception {
    Citation saved = saveRow("VEH1", "HIGHWAY-1", "100.00", "1000.00");

    mockMvc
        .perform(get("/api/citations/" + saved.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(saved.getId().intValue())))
        .andExpect(jsonPath("$.vehicleId", is("VEH1")))
        .andExpect(jsonPath("$.reason", containsString("HIGHWAY-1 limit 80 km/h")));

    mockMvc
        .perform(get("/api/citations/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status", is(404)))
        .andExpect(jsonPath("$.detail", is("No citation with id 999999")));
  }

  @Test
  @DisplayName("GET /api/analytics/summary -> totals per zone in INR")
  void analyticsSummaryWithKnownReadings() throws Exception {
    saveRow("V1", "SCHOOL-ZONE", "100.00", "1000.00");
    saveRow("V2", "SCHOOL-ZONE", "100.00", "1000.00");
    saveRow("V3", "HIGHWAY-1", "120.00", "2000.00");

    mockMvc
        .perform(get("/api/analytics/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCitations", is(3)))
        .andExpect(jsonPath("$.totalFineAmount", is(4000.00)))
        .andExpect(jsonPath("$.currency", is("INR")))
        .andExpect(jsonPath("$.totalViolations").doesNotExist())
        .andExpect(jsonPath("$.zones", hasSize(2)))
        .andExpect(jsonPath("$.zones[0].zone", is("SCHOOL-ZONE")))
        .andExpect(jsonPath("$.zones[0].citations", is(2)))
        .andExpect(jsonPath("$.zones[0].fineAmount", is(2000.00)))
        .andExpect(jsonPath("$.zones[1].zone", is("HIGHWAY-1")))
        .andExpect(jsonPath("$.zones[1].citations", is(1)));
  }

  @Test
  @DisplayName("GET /actuator/health/readiness -> 200 UP")
  void actuatorHealthReadiness() throws Exception {
    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("UP")));
  }

  @Test
  @DisplayName("Unknown paths -> 404, /api/violations is gone, wrong method -> 405")
  void unknownPathsAndMethods() throws Exception {
    mockMvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/nope")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/violations")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/readings")).andExpect(status().isMethodNotAllowed());
  }
}
