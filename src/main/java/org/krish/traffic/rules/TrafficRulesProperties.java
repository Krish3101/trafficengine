package org.krish.traffic.rules;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("traffic.rules")
public class TrafficRulesProperties {

  private String currency = "INR";
  private double defaultSpeedLimitKph = 80.0;
  private Map<String, Double> zoneSpeedLimits = new HashMap<>();
  private int defaultFine = 1000;
  private List<FineTier> fineTiers = new ArrayList<>();

  @PostConstruct
  public void validate() {
    if (currency == null || currency.isBlank()) {
      throw new IllegalStateException("traffic.rules.currency cannot be blank");
    }
    if (defaultSpeedLimitKph <= 0) {
      throw new IllegalStateException("traffic.rules.default-speed-limit-kph must be positive");
    }
    if (defaultFine < 0) {
      throw new IllegalStateException("traffic.rules.default-fine cannot be negative");
    }
    if (zoneSpeedLimits != null) {
      Map<String, Double> normalizedMap = new HashMap<>();
      for (Map.Entry<String, Double> entry : zoneSpeedLimits.entrySet()) {
        if (entry.getValue() == null || entry.getValue() <= 0) {
          throw new IllegalStateException(
              "Speed limit for zone " + entry.getKey() + " must be positive");
        }
        if (entry.getKey() != null) {
          normalizedMap.put(entry.getKey().trim().toUpperCase(Locale.ROOT), entry.getValue());
        }
      }
      this.zoneSpeedLimits = normalizedMap;
    }
    if (fineTiers != null) {
      for (FineTier tier : fineTiers) {
        if (tier.overByKph() < 0 || tier.amount() < 0) {
          throw new IllegalStateException("Fine tier values must be non-negative");
        }
      }
    }
  }

  public double getLimitForZone(String zone) {
    if (zone == null || zone.isBlank() || zoneSpeedLimits == null) {
      return defaultSpeedLimitKph;
    }
    String normalized = zone.trim().toUpperCase(Locale.ROOT);
    return zoneSpeedLimits.getOrDefault(normalized, defaultSpeedLimitKph);
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public double getDefaultSpeedLimitKph() {
    return defaultSpeedLimitKph;
  }

  public void setDefaultSpeedLimitKph(double defaultSpeedLimitKph) {
    this.defaultSpeedLimitKph = defaultSpeedLimitKph;
  }

  public Map<String, Double> getZoneSpeedLimits() {
    return zoneSpeedLimits;
  }

  public void setZoneSpeedLimits(Map<String, Double> zoneSpeedLimits) {
    this.zoneSpeedLimits = zoneSpeedLimits;
  }

  public int getDefaultFine() {
    return defaultFine;
  }

  public void setDefaultFine(int defaultFine) {
    this.defaultFine = defaultFine;
  }

  public List<FineTier> getFineTiers() {
    return fineTiers;
  }

  public void setFineTiers(List<FineTier> fineTiers) {
    this.fineTiers = fineTiers;
  }
}
