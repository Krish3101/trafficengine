package org.krish.traffic;

import org.krish.traffic.rules.SpeedRuleEngine;
import org.krish.traffic.rules.TrafficRulesProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the rule engine into Spring here, so the rules package itself has no Spring in it. */
@Configuration
class RulesConfig {

  @Bean(initMethod = "validate")
  @ConfigurationProperties("traffic.rules")
  TrafficRulesProperties trafficRulesProperties() {
    return new TrafficRulesProperties();
  }

  @Bean
  SpeedRuleEngine speedRuleEngine(TrafficRulesProperties properties) {
    return new SpeedRuleEngine(properties);
  }
}
