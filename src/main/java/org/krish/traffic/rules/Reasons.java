package org.krish.traffic.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Builds the one-line "why" text. The same function is used for a fresh evaluation (POST)
// and for a stored citation (GET), so both always say the same thing.
public final class Reasons {

  private Reasons() {}

  public static String explain(
      String version,
      String zone,
      BigDecimal limit,
      BigDecimal speed,
      Outcome outcome,
      BigDecimal tierOverBy,
      BigDecimal fine,
      String currency) {
    String head = version + ": " + zone + " limit " + num(limit) + " km/h; ";
    BigDecimal excess = speed.subtract(limit);
    return switch (outcome) {
      case WITHIN_LIMIT -> head + num(speed) + " km/h is at or under the limit";
      case EXEMPT -> head + "emergency vehicle exempt (would be +" + num(excess) + " over)";
      case VIOLATION -> {
        String tier = tierOverBy == null ? "" : ", above the +" + num(tierOverBy) + " tier";
        yield head + num(speed) + " km/h is +" + num(excess) + " over" + tier
            + ", fine " + money(fine, currency);
      }
    };
  }

  static String num(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  // ₹2,000 / ₹1,00,000 for INR (Indian grouping), "USD 2,000" style for anything else
  static String money(BigDecimal amount, String currency) {
    BigDecimal value = amount.stripTrailingZeros();
    if (value.scale() > 0) {
      value = value.setScale(2, RoundingMode.HALF_UP);
    }
    String plain = value.toPlainString();
    int dot = plain.indexOf('.');
    String whole = dot < 0 ? plain : plain.substring(0, dot);
    String fraction = dot < 0 ? "" : plain.substring(dot);

    if ("INR".equals(currency)) {
      return "₹" + groupIndian(whole) + fraction;
    }
    return currency + " " + whole.replaceAll("\\B(?=(\\d{3})+$)", ",") + fraction;
  }

  private static String groupIndian(String digits) {
    if (digits.length() <= 3) {
      return digits;
    }
    String lastThree = digits.substring(digits.length() - 3);
    String rest = digits.substring(0, digits.length() - 3);
    return rest.replaceAll("\\B(?=(\\d{2})+$)", ",") + "," + lastThree;
  }
}
