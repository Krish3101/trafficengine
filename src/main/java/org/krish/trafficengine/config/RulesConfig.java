package org.krish.trafficengine.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.krish.trafficengine.rules.Normalizer;
import org.krish.trafficengine.rules.RuleSet;
import org.krish.trafficengine.rules.RuleCatalog;
import org.krish.trafficengine.rules.SpeedRuleEngine;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@EnableConfigurationProperties(RulesConfig.RuleSetProperties.class)
public class RulesConfig {

  // Bound from the traffic.* block in application.yml; a bad value stops the app at startup.
  @Validated
  @ConfigurationProperties(prefix = "traffic")
  public record RuleSetProperties(
      @DefaultValue("Asia/Kolkata") String jurisdictionTimeZone,
      @NotEmpty List<@Valid RuleSetConfig> ruleSets) {}

  public record RuleSetConfig(
      @NotBlank String version,
      @NotNull LocalDate effectiveFrom,
      @DefaultValue("80.00") @Positive @Digits(integer = 3, fraction = 2)
          BigDecimal defaultSpeedLimitKph,
      List<@Valid ZoneConfig> zones,
      @NotEmpty List<@Valid FineTierConfig> fineTiers) {}

  public record ZoneConfig(
      @NotBlank String id,
      @NotNull @Positive @Digits(integer = 3, fraction = 2) BigDecimal limitKph) {}

  public record FineTierConfig(
      @NotNull @PositiveOrZero @Digits(integer = 3, fraction = 2) BigDecimal overByKph,
      @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal amount) {}

  @Bean
  public RuleCatalog ruleCatalog(RuleSetProperties properties) {
    ZoneId zoneId = ZoneId.of(properties.jurisdictionTimeZone());
    NavigableMap<Instant, RuleSet> byStart = new TreeMap<>();

    for (RuleSetConfig rs : properties.ruleSets()) {
      // a rule set starts at local midnight in the jurisdiction's time zone
      Instant start = rs.effectiveFrom().atStartOfDay(zoneId).toInstant();

      Map<String, BigDecimal> zoneLimits = new HashMap<>();
      if (rs.zones() != null) {
        rs.zones().forEach(z -> zoneLimits.put(Normalizer.normalizeZone(z.id()), z.limitKph()));
      }
      List<RuleSet.FineTier> tiers =
          rs.fineTiers().stream()
              .map(t -> new RuleSet.FineTier(t.overByKph(), t.amount()))
              .toList();

      byStart.put(start, new RuleSet(rs.version(), rs.defaultSpeedLimitKph(), zoneLimits, tiers));
    }
    return new RuleCatalog(byStart);
  }

  @Bean
  public SpeedRuleEngine speedRuleEngine(RuleCatalog catalog) {
    return new SpeedRuleEngine(catalog);
  }
}
