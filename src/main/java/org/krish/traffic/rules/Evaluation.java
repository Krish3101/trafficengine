package org.krish.traffic.rules;

import java.math.BigDecimal;

// defaultLimit is true when the zone is not in the rule set and the general limit was used
public record Evaluation(
    Outcome outcome,
    String ruleSetVersion,
    String zone,
    boolean defaultLimit,
    BigDecimal speedLimitKph,
    BigDecimal speedKph,
    BigDecimal excessKph,
    BigDecimal tierOverByKph,
    BigDecimal fineAmount,
    String currency) {

  public String reason() {
    return Reasons.explain(
        ruleSetVersion, zone, speedLimitKph, speedKph, outcome, tierOverByKph, fineAmount, currency);
  }
}
