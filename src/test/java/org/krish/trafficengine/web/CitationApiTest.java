package org.krish.trafficengine.web;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.krish.trafficengine.citation.Citation;
import org.krish.trafficengine.citation.CitationService;
import org.krish.trafficengine.citation.ZoneSummary;
import org.krish.trafficengine.rules.Evaluation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest
class CitationApiTest {

  private static final String REASON =
      "2026-10: SCHOOL-ZONE limit 25 km/h; 55 km/h is +30 over, above the +20 tier, fine ₹3000";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CitationService citationService;

  private ResultActions postReading(String json) throws Exception {
    return mockMvc.perform(
        post("/api/readings").contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private static Evaluation evaluation(Evaluation.Outcome outcome, String speed, String fine) {
    BigDecimal limit = new BigDecimal("25.00");
    BigDecimal s = new BigDecimal(speed);
    return new Evaluation(
        outcome, "2026-10", "SCHOOL-ZONE", limit, s, s.subtract(limit).max(BigDecimal.ZERO),
        new BigDecimal("20.00"), new BigDecimal(fine));
  }

  @Test
  void violationReturns201WithTheCitation() throws Exception {
    Instant now = Instant.parse("2026-10-05T10:00:00Z");
    Citation citation =
        new Citation(
            1L, "KA01AB1234", "SCHOOL-ZONE", new BigDecimal("55.00"), new BigDecimal("25.00"),
            new BigDecimal("30.00"), new BigDecimal("3000.00"), "2026-10", REASON, now, now);
    when(citationService.recordReading(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(
            new CitationService.Result(
                "KA01AB1234",
                evaluation(Evaluation.Outcome.VIOLATION, "55.00", "3000.00"),
                citation));

    postReading(
            """
            {"vehicleId": "KA 01 AB 1234", "zone": "school zone", "speedKph": 55}
            """)
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/citations/1"))
        .andExpect(jsonPath("$.outcome", is("VIOLATION")))
        .andExpect(jsonPath("$.reason", is(REASON)))
        .andExpect(jsonPath("$.citation.fineAmount", is(3000.00)))
        .andExpect(jsonPath("$.citation.ruleSetVersion", is("2026-10")));
  }

  @Test
  void withinLimitReturns200WithoutACitation() throws Exception {
    when(citationService.recordReading(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(
            new CitationService.Result(
                "KA01AB1234", evaluation(Evaluation.Outcome.WITHIN_LIMIT, "20.00", "0.00"), null));

    postReading(
            """
            {"vehicleId": "KA01AB1234", "zone": "SCHOOL-ZONE", "speedKph": 20}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("WITHIN_LIMIT")))
        .andExpect(jsonPath("$.citation", nullValue()));
  }

  @Test
  void badInputReturns400NamingTheField() throws Exception {
    postReading(
            """
            {"vehicleId": "", "zone": "SCHOOL-ZONE", "speedKph": 55}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field", is("vehicleId")));

    postReading(
            """
            {"vehicleId": "KA01AB1234", "zone": "SCHOOL-ZONE", "speedKph": 55,
             "observedAt": "2099-01-01T00:00:00Z"}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field", is("observedAt")));
  }

  @Test
  void unknownCitationReturns404() throws Exception {
    when(citationService.find(999L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/citations/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("No citation with id 999")));
  }

  @Test
  void getCitationByIdReturns200() throws Exception {
    Instant now = Instant.parse("2026-10-05T10:00:00Z");
    Citation citation =
        new Citation(
            1L, "KA01AB1234", "SCHOOL-ZONE", new BigDecimal("55.00"), new BigDecimal("25.00"),
            new BigDecimal("30.00"), new BigDecimal("3000.00"), "2026-10", REASON, now, now);
    when(citationService.find(1L)).thenReturn(Optional.of(citation));

    mockMvc
        .perform(get("/api/citations/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.vehicleId", is("KA01AB1234")))
        .andExpect(jsonPath("$.fineAmount", is(3000.00)));
  }

  @Test
  void listCitationsReturnsArray() throws Exception {
    Instant now = Instant.parse("2026-10-05T10:00:00Z");
    Citation citation =
        new Citation(
            1L, "KA01AB1234", "SCHOOL-ZONE", new BigDecimal("55.00"), new BigDecimal("25.00"),
            new BigDecimal("30.00"), new BigDecimal("3000.00"), "2026-10", REASON, now, now);
    when(citationService.list(null)).thenReturn(List.of(citation));

    mockMvc
        .perform(get("/api/citations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", is(1)))
        .andExpect(jsonPath("$[0].vehicleId", is("KA01AB1234")));
  }

  @Test
  void analyticsSummaryReturnsZoneTotals() throws Exception {
    ZoneSummary summary =
        new ZoneSummary() {
          @Override
          public String getZone() {
            return "SCHOOL-ZONE";
          }

          @Override
          public long getCount() {
            return 10L;
          }

          @Override
          public BigDecimal getFineTotal() {
            return new BigDecimal("30000.00");
          }
        };

    when(citationService.summary()).thenReturn(List.of(summary));

    mockMvc
        .perform(get("/api/analytics/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].zone", is("SCHOOL-ZONE")))
        .andExpect(jsonPath("$[0].count", is(10)))
        .andExpect(jsonPath("$[0].fineTotal", is(30000.00)));
  }
}
