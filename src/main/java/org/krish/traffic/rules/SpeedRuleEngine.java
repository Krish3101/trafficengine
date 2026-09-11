package org.krish.traffic.rules;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SpeedRuleEngine {

  private final TrafficRulesProperties properties;

  public SpeedRuleEngine(TrafficRulesProperties properties) {
    this.properties = properties;
  }

  public Evaluation evaluate(String zone, double speedKph, boolean emergency) {
    double limit = properties.getLimitForZone(zone);

    if (speedKph <= limit) {
      return new Evaluation(Outcome.WITHIN_LIMIT, limit, 0.0, 0);
    }

    double excess = BigDecimal.valueOf(speedKph).subtract(BigDecimal.valueOf(limit)).doubleValue();

    if (emergency) {
      return new Evaluation(Outcome.EXEMPT, limit, excess, 0);
    }

    int fine = calculateFine(excess);
    return new Evaluation(Outcome.VIOLATION, limit, excess, fine);
  }

  private int calculateFine(double excess) {
    List<FineTier> tiers = properties.getFineTiers();
    if (tiers == null || tiers.isEmpty()) {
      return properties.getDefaultFine();
    }

    return tiers.stream()
        .sorted(Comparator.comparingDouble(FineTier::overByKph).reversed())
        .filter(tier -> excess > tier.overByKph())
        .findFirst()
        .map(FineTier::amount)
        .orElse(properties.getDefaultFine());
  }
}
