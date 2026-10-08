package org.krish.trafficengine.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Evaluation(
    Outcome outcome,
    String ruleSetVersion,
    String zone,
    BigDecimal speedLimitKph,
    BigDecimal speedKph,
    BigDecimal excessKph,
    BigDecimal tierOverByKph,
    BigDecimal fineAmount) {

  public enum Outcome {
    WITHIN_LIMIT,
    EXEMPT,
    VIOLATION
  }

  // The one-line "why" text that is stored with each citation.
  public String reason() {
    String head = ruleSetVersion + ": " + zone + " limit " + num(speedLimitKph) + " km/h; ";
    return switch (outcome) {
      case WITHIN_LIMIT -> head + num(speedKph) + " km/h is at or under the limit";
      case EXEMPT -> head + "emergency vehicle exempt (would be +" + num(excessKph) + " over)";
      case VIOLATION ->
          head + num(speedKph) + " km/h is +" + num(excessKph) + " over, above the +"
              + num(tierOverByKph) + " tier, fine " + money(fineAmount);
    };
  }

  static String num(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  static String money(BigDecimal amount) {
    BigDecimal value = amount.stripTrailingZeros();
    if (value.scale() > 0) {
      value = value.setScale(2, RoundingMode.HALF_UP);
    }
    return "₹" + value.toPlainString();
  }
}
