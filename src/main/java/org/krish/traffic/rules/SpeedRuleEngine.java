package org.krish.traffic.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;

// Plain Java, no Spring: picks the rule set in force at observedAt and works out the fine.
public class SpeedRuleEngine {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  private final RuleSetCatalog catalog;

  public SpeedRuleEngine(RuleSetCatalog catalog) {
    this.catalog = catalog;
  }

  public Evaluation evaluate(
      String canonicalZone, BigDecimal speedKph, boolean emergency, Instant observedAt) {
    RuleSet ruleSet = catalog.findRuleSet(observedAt);

    boolean defaultLimit = !ruleSet.hasZone(canonicalZone);
    BigDecimal limit = ruleSet.getLimit(canonicalZone).setScale(2, RoundingMode.HALF_UP);
    BigDecimal speed = speedKph.setScale(2, RoundingMode.HALF_UP);

    if (speed.compareTo(limit) <= 0) {
      return new Evaluation(
          Outcome.WITHIN_LIMIT, ruleSet.version(), canonicalZone, defaultLimit,
          limit, speed, ZERO, null, ZERO, ruleSet.currency());
    }

    BigDecimal excess = speed.subtract(limit);
    if (emergency) {
      return new Evaluation(
          Outcome.EXEMPT, ruleSet.version(), canonicalZone, defaultLimit,
          limit, speed, excess, null, ZERO, ruleSet.currency());
    }

    // tier edges are strict: exactly +20 over stays in the +0 tier
    FineTier tier =
        ruleSet.fineTiers().stream()
            .filter(t -> excess.compareTo(t.overByKph()) > 0)
            .max(Comparator.comparing(FineTier::overByKph))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No fine tier for +" + excess + " in rule set " + ruleSet.version()));

    return new Evaluation(
        Outcome.VIOLATION, ruleSet.version(), canonicalZone, defaultLimit,
        limit, speed, excess,
        tier.overByKph().setScale(2, RoundingMode.HALF_UP),
        tier.amount().setScale(2, RoundingMode.HALF_UP),
        ruleSet.currency());
  }
}
