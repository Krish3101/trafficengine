package org.krish.trafficengine.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RuleSet(
    String version,
    BigDecimal defaultSpeedLimitKph,
    Map<String, BigDecimal> zoneLimits,
    List<FineTier> fineTiers) {

  public record FineTier(BigDecimal overByKph, BigDecimal amount) {}

  public RuleSet {
    zoneLimits = Map.copyOf(zoneLimits);
    fineTiers = List.copyOf(fineTiers);
  }

  public BigDecimal limitFor(String zone) {
    return zoneLimits.getOrDefault(zone, defaultSpeedLimitKph);
  }
}
