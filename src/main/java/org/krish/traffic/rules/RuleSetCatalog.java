package org.krish.traffic.rules;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class RuleSetCatalog {

  private final NavigableMap<Instant, RuleSet> ruleSets;

  public RuleSetCatalog(NavigableMap<Instant, RuleSet> ruleSets) {
    if (ruleSets == null || ruleSets.isEmpty()) {
      throw new IllegalArgumentException("Catalog must have at least one rule set");
    }
    this.ruleSets = Collections.unmodifiableNavigableMap(new TreeMap<>(ruleSets));
  }

  public RuleSet findRuleSet(Instant observedAt) {
    if (observedAt == null) {
      throw new IllegalArgumentException("observedAt cannot be null");
    }
    Map.Entry<Instant, RuleSet> entry = ruleSets.floorEntry(observedAt);
    if (entry == null) {
      throw new UnenforceableReadingException("No rule set in effect at " + observedAt);
    }
    return entry.getValue();
  }

  public NavigableMap<Instant, RuleSet> getRuleSets() {
    return ruleSets;
  }
}
