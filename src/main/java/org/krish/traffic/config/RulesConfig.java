package org.krish.traffic.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.krish.traffic.config.RuleSetProperties.RuleSetConfig;
import org.krish.traffic.rules.FineTier;
import org.krish.traffic.rules.Normalizer;
import org.krish.traffic.rules.RuleSet;
import org.krish.traffic.rules.RuleSetCatalog;
import org.krish.traffic.rules.SpeedRuleEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RuleSetProperties.class)
public class RulesConfig {

  @Bean
  public RuleSetCatalog ruleSetCatalog(RuleSetProperties properties) {
    RuleSetValidator.validate(properties);
    return toCatalog(properties);
  }

  @Bean
  public SpeedRuleEngine speedRuleEngine(RuleSetCatalog catalog) {
    return new SpeedRuleEngine(catalog);
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  static RuleSetCatalog toCatalog(RuleSetProperties properties) {
    ZoneId zoneId = ZoneId.of(properties.jurisdictionTimeZone());
    NavigableMap<Instant, RuleSet> byStart = new TreeMap<>();

    for (RuleSetConfig rs : properties.ruleSets()) {
      // a rule set starts at local midnight in the jurisdiction's time zone
      Instant start = rs.effectiveFrom().atStartOfDay(zoneId).toInstant();

      Map<String, BigDecimal> zoneLimits = new HashMap<>();
      if (rs.zones() != null) {
        rs.zones().forEach(z -> zoneLimits.put(Normalizer.canonicalizeZone(z.id()), z.limitKph()));
      }
      List<FineTier> tiers =
          rs.fineTiers().stream().map(t -> new FineTier(t.overByKph(), t.amount())).toList();

      byStart.put(
          start,
          new RuleSet(
              rs.version(),
              rs.effectiveFrom(),
              start,
              rs.currency(),
              rs.defaultSpeedLimitKph(),
              zoneLimits,
              tiers));
    }
    return new RuleSetCatalog(byStart);
  }
}
