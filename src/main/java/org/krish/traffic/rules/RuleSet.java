package org.krish.traffic.rules;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record RuleSet(
    String version,
    String currency,
    BigDecimal defaultSpeedLimitKph,
    Map<String, BigDecimal> zoneLimits,
    List<FineTier> fineTiers) {

  public RuleSet {
    zoneLimits = zoneLimits != null ? Map.copyOf(zoneLimits) : Collections.emptyMap();
    fineTiers = fineTiers != null ? List.copyOf(fineTiers) : Collections.emptyList();
  }

  public boolean hasZone(String canonicalZone) {
    return canonicalZone != null && zoneLimits.containsKey(canonicalZone);
  }

  public BigDecimal getLimit(String canonicalZone) {
    if (canonicalZone != null && zoneLimits.containsKey(canonicalZone)) {
      return zoneLimits.get(canonicalZone);
    }
    return defaultSpeedLimitKph;
  }
}
