package org.krish.trafficengine.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;

// Plain Java, no Spring: picks the rule set in force at observedAt and works out the fine.
public class SpeedRuleEngine {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  private final RuleCatalog catalog;

  public SpeedRuleEngine(RuleCatalog catalog) {
    this.catalog = catalog;
  }

  public Evaluation evaluate(
      String zone, BigDecimal speedKph, boolean emergency, Instant observedAt) {
    RuleSet ruleSet = catalog.findRuleSet(observedAt);

    BigDecimal limit = ruleSet.limitFor(zone).setScale(2, RoundingMode.HALF_UP);
    BigDecimal speed = speedKph.setScale(2, RoundingMode.HALF_UP);

    if (speed.compareTo(limit) <= 0) {
      return new Evaluation(
          Evaluation.Outcome.WITHIN_LIMIT, ruleSet.version(), zone,
          limit, speed, ZERO, null, ZERO);
    }

    BigDecimal excess = speed.subtract(limit);
    if (emergency) {
      return new Evaluation(
          Evaluation.Outcome.EXEMPT, ruleSet.version(), zone,
          limit, speed, excess, null, ZERO);
    }

    // tier edges are strict: exactly +20 over stays in the +0 tier
    RuleSet.FineTier tier =
        ruleSet.fineTiers().stream()
            .filter(t -> excess.compareTo(t.overByKph()) > 0)
            .max(Comparator.comparing(RuleSet.FineTier::overByKph))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No fine tier for +" + excess + " in rule set " + ruleSet.version()));

    return new Evaluation(
        Evaluation.Outcome.VIOLATION, ruleSet.version(), zone,
        limit, speed, excess,
        tier.overByKph().setScale(2, RoundingMode.HALF_UP),
        tier.amount().setScale(2, RoundingMode.HALF_UP));
  }
}
