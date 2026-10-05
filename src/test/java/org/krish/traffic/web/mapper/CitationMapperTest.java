package org.krish.traffic.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.krish.traffic.citation.Citation;
import org.krish.traffic.rules.Evaluation;
import org.krish.traffic.rules.FineTier;
import org.krish.traffic.rules.RuleSet;
import org.krish.traffic.rules.RuleSetCatalog;
import org.krish.traffic.rules.SpeedRuleEngine;

class CitationMapperTest {

  private static final Instant START = Instant.parse("2025-12-31T18:30:00Z");

  private final SpeedRuleEngine engine = engine();

  private static SpeedRuleEngine engine() {
    RuleSet rules =
        new RuleSet(
            "2026-09",
            null,
            START,
            "INR",
            new BigDecimal("80.00"),
            Map.of("SCHOOL-ZONE", new BigDecimal("30.00")),
            List.of(
                new FineTier(new BigDecimal("0.00"), new BigDecimal("1000.00")),
                new FineTier(new BigDecimal("20.00"), new BigDecimal("2000.00")),
                new FineTier(new BigDecimal("40.00"), new BigDecimal("5000.00"))));
    NavigableMap<Instant, RuleSet> map = new TreeMap<>();
    map.put(START, rules);
    return new SpeedRuleEngine(new RuleSetCatalog(map));
  }

  // what CitationService would store for this evaluation
  private static Citation stored(Evaluation e) {
    return new Citation(
        7L, "KA01AB1234", e.zone(), e.speedKph(), e.speedLimitKph(), e.excessKph(),
        e.tierOverByKph(), e.fineAmount(), e.currency(), e.ruleSetVersion(), START, START);
  }

  @Test
  void reasonFromEvaluationEqualsReasonFromStoredRow() {
    for (String speed : List.of("30.01", "55", "50.00", "70.01", "120.5")) {
      Evaluation e = engine.evaluate("SCHOOL-ZONE", new BigDecimal(speed), false, START);
      assertThat(CitationMapper.toResponse(stored(e)).reason()).isEqualTo(e.reason());
    }
    Evaluation unknownZone = engine.evaluate("MARKET-ST", new BigDecimal("95"), false, START);
    assertThat(CitationMapper.toResponse(stored(unknownZone)).reason()).isEqualTo(unknownZone.reason());
  }

  @Test
  void legacyRowWithoutTierStillGetsAReason() {
    Citation legacy =
        new Citation(
            1L, "KA 01 AB 1234", "SCHOOL-ZONE", new BigDecimal("55.00"), new BigDecimal("30.00"),
            new BigDecimal("25.00"), null, new BigDecimal("999.00"), "INR", "legacy-unverified",
            START, START);
    assertThat(CitationMapper.toResponse(legacy).reason())
        .isEqualTo("legacy-unverified: SCHOOL-ZONE limit 30 km/h; 55 km/h is +25 over, fine ₹999");
  }
}
