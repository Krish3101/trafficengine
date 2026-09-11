package org.krish.traffic.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpeedRuleEngineTest {

  private TrafficRulesProperties properties;
  private SpeedRuleEngine engine;

  @BeforeEach
  void setUp() {
    properties = new TrafficRulesProperties();
    properties.setCurrency("INR");
    properties.setDefaultSpeedLimitKph(80.0);
    properties.setZoneSpeedLimits(Map.of("SCHOOL-ZONE", 30.0, "HIGHWAY-1", 100.0));
    properties.setDefaultFine(1000);
    properties.setFineTiers(
        List.of(new FineTier(40.0, 5000), new FineTier(20.0, 2000), new FineTier(0.0, 1000)));
    properties.validate();
    engine = new SpeedRuleEngine(properties);
  }

  @Test
  @DisplayName("80.0 -> WITHIN_LIMIT, no fine")
  void withinLimitAtExactSpeed() {
    Evaluation eval = engine.evaluate("ZONE-A", 80.0, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.WITHIN_LIMIT);
    assertThat(eval.fineAmount()).isZero();
    assertThat(eval.speedLimitKph()).isEqualTo(80.0);
    assertThat(eval.excessKph()).isZero();
  }

  @Test
  @DisplayName("80.1 -> VIOLATION, 1000")
  void violationJustAboveLimit() {
    Evaluation eval = engine.evaluate("ZONE-A", 80.1, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.fineAmount()).isEqualTo(1000);
    assertThat(eval.excessKph()).isEqualTo(0.1);
  }

  @Test
  @DisplayName("100.0 -> VIOLATION, 1000 (tier boundary is exclusive)")
  void tierBoundaryExclusiveLowerTier() {
    // excess = 100.0 - 80.0 = 20.0. Since 20.0 is not > 20.0, falls to tier > 0.0 -> 1000
    Evaluation eval = engine.evaluate("ZONE-A", 100.0, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.fineAmount()).isEqualTo(1000);
    assertThat(eval.excessKph()).isEqualTo(20.0);
  }

  @Test
  @DisplayName("100.1 -> VIOLATION, 2000")
  void justAboveTierBoundary() {
    // excess = 100.1 - 80.0 = 20.1 > 20.0 -> 2000
    Evaluation eval = engine.evaluate("ZONE-A", 100.1, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.fineAmount()).isEqualTo(2000);
    assertThat(eval.excessKph()).isEqualTo(20.1);
  }

  @Test
  @DisplayName("120.0 -> VIOLATION, 2000")
  void atUpperTierBoundary() {
    // excess = 120.0 - 80.0 = 40.0. Since 40.0 is not > 40.0, falls to tier > 20.0 -> 2000
    Evaluation eval = engine.evaluate("ZONE-A", 120.0, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.fineAmount()).isEqualTo(2000);
    assertThat(eval.excessKph()).isEqualTo(40.0);
  }

  @Test
  @DisplayName("120.1 -> VIOLATION, 5000")
  void aboveHighestTierBoundary() {
    // excess = 120.1 - 80.0 = 40.1 > 40.0 -> 5000
    Evaluation eval = engine.evaluate("ZONE-A", 120.1, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.fineAmount()).isEqualTo(5000);
    assertThat(eval.excessKph()).isEqualTo(40.1);
  }

  @Test
  @DisplayName("150.0, emergency -> EXEMPT, no fine")
  void emergencySpeedingIsExempt() {
    Evaluation eval = engine.evaluate("ZONE-A", 150.0, true);
    assertThat(eval.outcome()).isEqualTo(Outcome.EXEMPT);
    assertThat(eval.fineAmount()).isZero();
    assertThat(eval.excessKph()).isEqualTo(70.0);
  }

  @Test
  @DisplayName("50.0, emergency -> WITHIN_LIMIT")
  void emergencyUnderLimitIsWithinLimit() {
    Evaluation eval = engine.evaluate("ZONE-A", 50.0, true);
    assertThat(eval.outcome()).isEqualTo(Outcome.WITHIN_LIMIT);
    assertThat(eval.fineAmount()).isZero();
    assertThat(eval.excessKph()).isZero();
  }

  @Test
  @DisplayName("45.0 in SCHOOL-ZONE -> VIOLATION, limit 30.0, excess 15.0, fine 1000")
  void schoolZoneViolation() {
    Evaluation eval = engine.evaluate("SCHOOL-ZONE", 45.0, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.speedLimitKph()).isEqualTo(30.0);
    assertThat(eval.excessKph()).isEqualTo(15.0);
    assertThat(eval.fineAmount()).isEqualTo(1000);
  }

  @Test
  @DisplayName("95.0 in HIGHWAY-1 -> WITHIN_LIMIT")
  void highwayUnderLimit() {
    Evaluation eval = engine.evaluate("HIGHWAY-1", 95.0, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.WITHIN_LIMIT);
    assertThat(eval.speedLimitKph()).isEqualTo(100.0);
    assertThat(eval.fineAmount()).isZero();
  }

  @Test
  @DisplayName("95.0 in UNKNOWN-ZONE -> Default limit 80.0 -> VIOLATION, 1000")
  void unknownZoneUsesDefaultLimit() {
    Evaluation eval = engine.evaluate("UNKNOWN-ZONE", 95.0, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.speedLimitKph()).isEqualTo(80.0);
    assertThat(eval.excessKph()).isEqualTo(15.0);
    assertThat(eval.fineAmount()).isEqualTo(1000);
  }

  @Test
  @DisplayName("130.0 with no tiers configured -> VIOLATION, fine = default-fine")
  void noTiersUsesDefaultFine() {
    TrafficRulesProperties noTierProps = new TrafficRulesProperties();
    noTierProps.setCurrency("INR");
    noTierProps.setDefaultSpeedLimitKph(80.0);
    noTierProps.setDefaultFine(1000);
    noTierProps.setFineTiers(Collections.emptyList());
    noTierProps.validate();

    SpeedRuleEngine noTierEngine = new SpeedRuleEngine(noTierProps);
    Evaluation eval = noTierEngine.evaluate("ZONE-A", 130.0, false);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
    assertThat(eval.fineAmount()).isEqualTo(1000);
  }

  @Test
  @DisplayName("zone ' school-zone ' -> Resolves to SCHOOL-ZONE, limit 30.0")
  void zoneNormalisationCaseAndWhitespace() {
    Evaluation eval = engine.evaluate(" school-zone ", 45.0, false);
    assertThat(eval.speedLimitKph()).isEqualTo(30.0);
    assertThat(eval.outcome()).isEqualTo(Outcome.VIOLATION);
  }
}
