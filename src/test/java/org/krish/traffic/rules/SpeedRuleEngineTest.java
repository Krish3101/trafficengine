package org.krish.traffic.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpeedRuleEngineTest {

  private SpeedRuleEngine engine;
  private Instant sepBoundary;
  private Instant octBoundary;

  @BeforeEach
  void setUp() {
    ZoneId zoneId = ZoneId.of("Asia/Kolkata");

    LocalDate d1 = LocalDate.of(2026, 1, 1);
    sepBoundary = d1.atStartOfDay(zoneId).toInstant();

    RuleSet set1 =
        new RuleSet(
            "2026-09",
            "INR",
            new BigDecimal("80.00"),
            Map.of(
                "SCHOOL-ZONE", new BigDecimal("30.00"),
                "HIGHWAY-1", new BigDecimal("100.00"),
                "MAIN-ROAD", new BigDecimal("80.00")),
            List.of(
                new FineTier(new BigDecimal("40.00"), new BigDecimal("5000.00")),
                new FineTier(new BigDecimal("20.00"), new BigDecimal("2000.00")),
                new FineTier(new BigDecimal("0.00"), new BigDecimal("1000.00"))));

    LocalDate d2 = LocalDate.of(2026, 10, 1);
    octBoundary = d2.atStartOfDay(zoneId).toInstant();

    RuleSet set2 =
        new RuleSet(
            "2026-10",
            "INR",
            new BigDecimal("80.00"),
            Map.of(
                "SCHOOL-ZONE", new BigDecimal("25.00"),
                "HIGHWAY-1", new BigDecimal("100.00"),
                "MAIN-ROAD", new BigDecimal("80.00")),
            List.of(
                new FineTier(new BigDecimal("40.00"), new BigDecimal("6000.00")),
                new FineTier(new BigDecimal("20.00"), new BigDecimal("3000.00")),
                new FineTier(new BigDecimal("0.00"), new BigDecimal("1500.00"))));

    NavigableMap<Instant, RuleSet> catalogMap = new TreeMap<>();
    catalogMap.put(sepBoundary, set1);
    catalogMap.put(octBoundary, set2);

    RuleSetCatalog catalog = new RuleSetCatalog(catalogMap);
    engine = new SpeedRuleEngine(catalog);
  }

  @Test
  @DisplayName("Timestamp before first effective date -> throws UnenforceableReadingException")
  void beforeFirstEffectiveDateThrows() {
    Instant before = sepBoundary.minusSeconds(1);
    assertThatThrownBy(() -> engine.evaluate("MAIN-ROAD", new BigDecimal("80.00"), false, before))
        .isInstanceOf(UnenforceableReadingException.class);
  }

  @Test
  @DisplayName("Effective date boundary: 1 ms before boundary uses 2026-09, at boundary uses 2026-10")
  void effectiveDateBoundarySelection() {
    Instant justBefore = octBoundary.minusMillis(1);
    Evaluation evalBefore =
        engine.evaluate("SCHOOL-ZONE", new BigDecimal("50.00"), false, justBefore);
    assertThat(evalBefore.ruleSetVersion()).isEqualTo("2026-09");
    assertThat(evalBefore.speedLimitKph()).isEqualByComparingTo("30.00");
    assertThat(evalBefore.excessKph()).isEqualByComparingTo("20.00");
    assertThat(evalBefore.fineAmount()).isEqualByComparingTo("1000.00");

    Instant atBoundary = octBoundary;
    Evaluation evalAt = engine.evaluate("SCHOOL-ZONE", new BigDecimal("50.00"), false, atBoundary);
    assertThat(evalAt.ruleSetVersion()).isEqualTo("2026-10");
    assertThat(evalAt.speedLimitKph()).isEqualByComparingTo("25.00");
    assertThat(evalAt.excessKph()).isEqualByComparingTo("25.00");
    assertThat(evalAt.fineAmount()).isEqualByComparingTo("3000.00");
  }

  @Test
  @DisplayName("Exact speed limit -> WITHIN_LIMIT, 0 excess, 0 fine")
  void withinLimitAtExactSpeed() {
    Evaluation eval = engine.evaluate("MAIN-ROAD", new BigDecimal("80.00"), false, sepBoundary);
    assertThat(eval.outcome()).isEqualTo(Outcome.WITHIN_LIMIT);
    assertThat(eval.fineAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(eval.speedLimitKph()).isEqualByComparingTo("80.00");
    assertThat(eval.excessKph()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(eval.reason()).contains("at or under the limit");
  }

  @Test
  @DisplayName("Under speed limit -> WITHIN_LIMIT")
  void underSpeedLimit() {
    Evaluation eval = engine.evaluate("MAIN-ROAD", new BigDecimal("75.50"), false, sepBoundary);
    assertThat(eval.outcome()).isEqualTo(Outcome.WITHIN_LIMIT);
    assertThat(eval.fineAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(eval.excessKph()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("Strict tier boundary at 20.00: 100.00 in 80 zone is excess 20.00 -> tier 0 -> 1000")
  void tierBoundaryExclusiveAt20() {
    Evaluation eval = engine.evaluate("MAIN-ROAD", new BigDecimal("100.00"), false, sepBoundary);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.excessKph()).isEqualByComparingTo("20.00");
    assertThat(eval.tierOverByKph()).isEqualByComparingTo("0.00");
    assertThat(eval.fineAmount()).isEqualByComparingTo("1000.00");
  }

  @Test
  @DisplayName("Strict tier boundary above 20.00: 100.01 in 80 zone is excess 20.01 -> tier 20 -> 2000")
  void tierBoundaryJustAbove20() {
    Evaluation eval = engine.evaluate("MAIN-ROAD", new BigDecimal("100.01"), false, sepBoundary);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.excessKph()).isEqualByComparingTo("20.01");
    assertThat(eval.tierOverByKph()).isEqualByComparingTo("20.00");
    assertThat(eval.fineAmount()).isEqualByComparingTo("2000.00");
  }

  @Test
  @DisplayName("Strict tier boundary at 40.00: 120.00 in 80 zone is excess 40.00 -> tier 20 -> 2000")
  void tierBoundaryExclusiveAt40() {
    Evaluation eval = engine.evaluate("MAIN-ROAD", new BigDecimal("120.00"), false, sepBoundary);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.excessKph()).isEqualByComparingTo("40.00");
    assertThat(eval.tierOverByKph()).isEqualByComparingTo("20.00");
    assertThat(eval.fineAmount()).isEqualByComparingTo("2000.00");
  }

  @Test
  @DisplayName("Strict tier boundary above 40.00: 120.01 in 80 zone is excess 40.01 -> tier 40 -> 5000")
  void tierBoundaryJustAbove40() {
    Evaluation eval = engine.evaluate("MAIN-ROAD", new BigDecimal("120.01"), false, sepBoundary);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.excessKph()).isEqualByComparingTo("40.01");
    assertThat(eval.tierOverByKph()).isEqualByComparingTo("40.00");
    assertThat(eval.fineAmount()).isEqualByComparingTo("5000.00");
  }

  @Test
  @DisplayName("Emergency speeding -> EXEMPT, fine 0")
  void emergencySpeedingIsExempt() {
    Evaluation eval = engine.evaluate("MAIN-ROAD", new BigDecimal("150.00"), true, sepBoundary);
    assertThat(eval.outcome()).isEqualTo(Outcome.EXEMPT);
    assertThat(eval.fineAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(eval.excessKph()).isEqualByComparingTo("70.00");
    assertThat(eval.reason()).contains("emergency vehicle exempt");
  }

  @Test
  @DisplayName("Emergency under limit -> WITHIN_LIMIT")
  void emergencyUnderLimitIsWithinLimit() {
    Evaluation eval = engine.evaluate("MAIN-ROAD", new BigDecimal("50.00"), true, sepBoundary);
    assertThat(eval.outcome()).isEqualTo(Outcome.WITHIN_LIMIT);
    assertThat(eval.fineAmount()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("Unknown zone uses default limit 80.00 and reason explains default")
  void unknownZoneUsesDefaultLimit() {
    Evaluation eval = engine.evaluate("MARKET-ST", new BigDecimal("95.00"), false, sepBoundary);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.defaultLimit()).isTrue();
    assertThat(eval.speedLimitKph()).isEqualByComparingTo("80.00");
    assertThat(eval.excessKph()).isEqualByComparingTo("15.00");
    assertThat(eval.fineAmount()).isEqualByComparingTo("1000.00");
    assertThat(eval.reason()).isEqualTo("2026-09: MARKET-ST limit 80 km/h; 95 km/h is +15 over, above the +0 tier, fine ₹1,000");
  }

  @Test
  @DisplayName("Normalizer canonicalizes zone names and vehicle IDs correctly")
  void normalizerChecks() {
    assertThat(Normalizer.canonicalizeZone("  school-zone  ")).isEqualTo("SCHOOL-ZONE");
    assertThat(Normalizer.canonicalizeZone("school_zone")).isEqualTo("SCHOOL-ZONE");
    assertThat(Normalizer.canonicalizeZone("market   road")).isEqualTo("MARKET-ROAD");

    assertThat(Normalizer.canonicalizeVehicleId("  ka-01-ab-1234  ")).isEqualTo("KA01AB1234");
    assertThat(Normalizer.canonicalizeVehicleId("KA 01 AB 1234")).isEqualTo("KA01AB1234");

    assertThatThrownBy(() -> Normalizer.canonicalizeZone("?"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Normalizer.canonicalizeVehicleId("A"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Reason text for all three outcomes")
  void reasonFormats() {
    assertThat(engine.evaluate("SCHOOL-ZONE", new BigDecimal("55"), false, sepBoundary).reason())
        .isEqualTo(
            "2026-09: SCHOOL-ZONE limit 30 km/h; 55 km/h is +25 over, above the +20 tier, fine ₹2,000");
    assertThat(engine.evaluate("SCHOOL-ZONE", new BigDecimal("30.00"), false, sepBoundary).reason())
        .isEqualTo("2026-09: SCHOOL-ZONE limit 30 km/h; 30 km/h is at or under the limit");
    assertThat(engine.evaluate("SCHOOL-ZONE", new BigDecimal("52.50"), true, sepBoundary).reason())
        .isEqualTo("2026-09: SCHOOL-ZONE limit 30 km/h; emergency vehicle exempt (would be +22.5 over)");
  }

  @Test
  @DisplayName("Money uses Indian grouping for INR and keeps paise only when present")
  void moneyFormat() {
    assertThat(Reasons.money(new BigDecimal("2000.00"), "INR")).isEqualTo("₹2,000");
    assertThat(Reasons.money(new BigDecimal("100000.00"), "INR")).isEqualTo("₹1,00,000");
    assertThat(Reasons.money(new BigDecimal("12345678.50"), "INR")).isEqualTo("₹1,23,45,678.50");
    assertThat(Reasons.money(new BigDecimal("999"), "INR")).isEqualTo("₹999");
    assertThat(Reasons.money(new BigDecimal("1234567.00"), "USD")).isEqualTo("USD 1,234,567");
  }

  @Test
  @DisplayName("The same reading either side of 2026-10-01 gives two different, explained results")
  void sameReadingUnderTwoRuleSets() {
    Evaluation before =
        engine.evaluate("SCHOOL-ZONE", new BigDecimal("28"), false, octBoundary.minusNanos(1000));
    Evaluation after = engine.evaluate("SCHOOL-ZONE", new BigDecimal("28"), false, octBoundary);

    assertThat(before.outcome()).isEqualTo(Outcome.WITHIN_LIMIT);
    assertThat(before.reason())
        .isEqualTo("2026-09: SCHOOL-ZONE limit 30 km/h; 28 km/h is at or under the limit");
    assertThat(after.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(after.reason())
        .isEqualTo("2026-10: SCHOOL-ZONE limit 25 km/h; 28 km/h is +3 over, above the +0 tier, fine ₹1,500");
  }

  @Test
  @DisplayName("Default-limit zones are flagged but the reason has no 'not configured' text")
  void defaultLimitFlag() {
    Evaluation eval = engine.evaluate("SCHOLL-ZONE", new BigDecimal("55"), false, sepBoundary);
    assertThat(eval.defaultLimit()).isTrue();
    assertThat(eval.outcome()).isEqualTo(Outcome.WITHIN_LIMIT);
    assertThat(eval.reason()).doesNotContain("not configured");
    assertThat(engine.evaluate("SCHOOL-ZONE", new BigDecimal("55"), false, sepBoundary).defaultLimit())
        .isFalse();
  }
}
