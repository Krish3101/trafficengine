package org.krish.traffic.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Bound from the traffic.* block in application.yml. RuleSetValidator checks it at startup.
@ConfigurationProperties(prefix = "traffic")
public record RuleSetProperties(
    @DefaultValue("Asia/Kolkata") String jurisdictionTimeZone,
    @DefaultValue("P90D") Duration maxReadingAge,
    @DefaultValue("PT5M") Duration maxClockSkew,
    List<RuleSetConfig> ruleSets) {

  public record RuleSetConfig(
      String version,
      LocalDate effectiveFrom,
      @DefaultValue("INR") String currency,
      @DefaultValue("80.00") BigDecimal defaultSpeedLimitKph,
      List<ZoneConfig> zones,
      List<FineTierConfig> fineTiers) {}

  public record ZoneConfig(String id, BigDecimal limitKph) {}

  public record FineTierConfig(BigDecimal overByKph, BigDecimal amount) {}
}
