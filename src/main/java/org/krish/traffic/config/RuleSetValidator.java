package org.krish.traffic.config;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.krish.traffic.config.RuleSetProperties.FineTierConfig;
import org.krish.traffic.config.RuleSetProperties.RuleSetConfig;
import org.krish.traffic.config.RuleSetProperties.ZoneConfig;
import org.krish.traffic.rules.Normalizer;

// Collects every problem in the config so startup fails once with the full list.
public final class RuleSetValidator {

  private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9A-Za-z._-]{1,32}$");
  private static final BigDecimal MAX_LIMIT = new BigDecimal("300");
  private static final Duration MAX_SKEW = Duration.ofMinutes(5);

  private RuleSetValidator() {}

  public static void validate(RuleSetProperties props) {
    List<String> errors = new ArrayList<>();

    try {
      ZoneId.of(props.jurisdictionTimeZone());
    } catch (DateTimeException | NullPointerException e) {
      errors.add("invalid jurisdiction-time-zone '" + props.jurisdictionTimeZone() + "'");
    }
    if (!isPositive(props.maxReadingAge())) {
      errors.add("max-reading-age must be positive");
    }
    if (!isPositive(props.maxClockSkew())) {
      errors.add("max-clock-skew must be positive");
    } else if (props.maxClockSkew().compareTo(MAX_SKEW) > 0) {
      // citations_observed_ck in V3 allows at most 5 minutes
      errors.add("max-clock-skew must be at most PT5M (the database check)");
    }

    List<RuleSetConfig> ruleSets = props.ruleSets() == null ? List.of() : props.ruleSets();
    if (ruleSets.isEmpty()) {
      errors.add("at least one rule set is needed");
    }

    Set<String> versions = new HashSet<>();
    Set<String> currencies = new HashSet<>();
    LocalDate lastFrom = null;
    for (int i = 0; i < ruleSets.size(); i++) {
      RuleSetConfig rs = ruleSets.get(i);
      String at = "rule-sets[" + i + "]: ";

      if (rs.version() == null || !VERSION_PATTERN.matcher(rs.version()).matches()) {
        errors.add(at + "version must match " + VERSION_PATTERN.pattern());
      } else if ("legacy-unverified".equalsIgnoreCase(rs.version())) {
        errors.add(at + "version 'legacy-unverified' is reserved for migrated rows");
      } else if (!versions.add(rs.version())) {
        errors.add(at + "duplicate version '" + rs.version() + "'");
      }

      if (rs.effectiveFrom() == null) {
        errors.add(at + "effective-from is missing");
      } else {
        if (lastFrom != null && !rs.effectiveFrom().isAfter(lastFrom)) {
          errors.add(at + "effective-from must be later than the previous rule set");
        }
        lastFrom = rs.effectiveFrom();
      }

      int moneyDigits = 2;
      try {
        moneyDigits = Currency.getInstance(rs.currency()).getDefaultFractionDigits();
        currencies.add(rs.currency());
      } catch (IllegalArgumentException | NullPointerException e) {
        errors.add(at + "invalid currency '" + rs.currency() + "'");
      }

      if (!isValidLimit(rs.defaultSpeedLimitKph())) {
        errors.add(at + "default-speed-limit-kph must be > 0, <= 300, with at most 2 decimals");
      }
      checkZones(at, rs.zones(), errors);
      checkTiers(at, rs.fineTiers(), moneyDigits, errors);
    }

    // the summary adds fines across rule sets, so they must share one currency
    if (currencies.size() > 1) {
      errors.add("all rule sets must use the same currency, found " + new TreeSet<>(currencies));
    }

    if (!errors.isEmpty()) {
      throw new IllegalStateException(
          "Invalid traffic rule config:\n - " + String.join("\n - ", errors));
    }
  }

  private static void checkZones(String at, List<ZoneConfig> zones, List<String> errors) {
    if (zones == null) {
      return;
    }
    Set<String> seen = new HashSet<>();
    for (ZoneConfig zone : zones) {
      try {
        if (zone.id() == null) {
          throw new IllegalArgumentException("missing");
        }
        if (!seen.add(Normalizer.canonicalizeZone(zone.id()))) {
          errors.add(at + "duplicate zone id '" + zone.id() + "'");
        }
      } catch (IllegalArgumentException e) {
        errors.add(at + "invalid zone id '" + zone.id() + "'");
      }
      if (!isValidLimit(zone.limitKph())) {
        errors.add(at + "limit for zone '" + zone.id() + "' must be > 0, <= 300, with at most 2 decimals");
      }
    }
  }

  private static void checkTiers(
      String at, List<FineTierConfig> tiers, int moneyDigits, List<String> errors) {
    if (tiers == null || tiers.isEmpty()) {
      errors.add(at + "fine-tiers is empty");
      return;
    }

    List<FineTierConfig> usable = new ArrayList<>();
    for (FineTierConfig tier : tiers) {
      if (tier.overByKph() == null || tier.overByKph().signum() < 0) {
        errors.add(at + "fine tier over-by-kph must be set and >= 0");
      } else if (tier.overByKph().scale() > 2) {
        errors.add(at + "fine tier over-by-kph " + tier.overByKph() + " has more than 2 decimals");
      } else if (tier.amount() == null || tier.amount().signum() < 0) {
        errors.add(at + "fine tier amount must be set and >= 0");
      } else if (tier.amount().scale() > moneyDigits) {
        errors.add(at + "fine tier amount " + tier.amount() + " has more than " + moneyDigits + " decimals");
      } else {
        usable.add(tier);
      }
    }

    usable.sort(Comparator.comparing(FineTierConfig::overByKph));
    if (usable.stream().noneMatch(t -> t.overByKph().signum() == 0)) {
      errors.add(at + "fine tiers need a tier with over-by-kph 0");
    }
    for (int i = 1; i < usable.size(); i++) {
      FineTierConfig prev = usable.get(i - 1);
      FineTierConfig cur = usable.get(i);
      if (cur.overByKph().compareTo(prev.overByKph()) == 0) {
        errors.add(at + "duplicate fine tier over-by-kph " + cur.overByKph());
      } else if (cur.amount().compareTo(prev.amount()) < 0) {
        errors.add(at + "fine for +" + cur.overByKph() + " is lower than the fine for +" + prev.overByKph());
      }
    }
  }

  private static boolean isPositive(Duration d) {
    return d != null && d.isPositive();
  }

  private static boolean isValidLimit(BigDecimal limit) {
    return limit != null
        && limit.signum() > 0
        && limit.compareTo(MAX_LIMIT) <= 0
        && limit.scale() <= 2;
  }
}
