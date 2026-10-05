package org.krish.traffic.rules;

import java.math.BigDecimal;

public record FineTier(BigDecimal overByKph, BigDecimal amount) {
  public FineTier {
    if (overByKph == null || overByKph.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("overByKph must be non-negative");
    }
    if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("amount must be non-negative");
    }
  }
}
