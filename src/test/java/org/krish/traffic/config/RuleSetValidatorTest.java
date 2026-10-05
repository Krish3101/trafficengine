package org.krish.traffic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.krish.traffic.config.RuleSetProperties.FineTierConfig;
import org.krish.traffic.config.RuleSetProperties.RuleSetConfig;
import org.krish.traffic.config.RuleSetProperties.ZoneConfig;
import org.krish.traffic.rules.RuleSetCatalog;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class RuleSetValidatorTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate OCT = LocalDate.of(2026, 10, 1);

  private static BigDecimal d(String value) {
    return new BigDecimal(value);
  }

  private static List<FineTierConfig> tiers() {
    return List.of(
        new FineTierConfig(d("0.00"), d("1000.00")),
        new FineTierConfig(d("20.00"), d("2000.00")),
        new FineTierConfig(d("40.00"), d("5000.00")));
  }

  private static List<ZoneConfig> zones() {
    return List.of(new ZoneConfig("SCHOOL-ZONE", d("30.00")), new ZoneConfig("HIGHWAY-1", d("100.00")));
  }

  private static RuleSetConfig ruleSet(String version, LocalDate from) {
    return new RuleSetConfig(version, from, "INR", d("80.00"), zones(), tiers());
  }

  private static RuleSetProperties props(RuleSetConfig... sets) {
    return new RuleSetProperties(
        "Asia/Kolkata", Duration.ofDays(90), Duration.ofMinutes(5), new ArrayList<>(List.of(sets)));
  }

  private static void assertInvalid(RuleSetProperties props, String expected) {
    assertThatThrownBy(() -> RuleSetValidator.validate(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(expected);
  }

  @Test
  void validConfigPassesAndBuildsCatalog() {
    RuleSetProperties props = props(ruleSet("2026-09", JAN), ruleSet("2026-10", OCT));
    assertThatCode(() -> RuleSetValidator.validate(props)).doesNotThrowAnyException();

    RuleSetCatalog catalog = RulesConfig.toCatalog(props);
    assertThat(catalog.getRuleSets()).hasSize(2);
  }

  @Test
  void duplicateVersion() {
    assertInvalid(props(ruleSet("2026-09", JAN), ruleSet("2026-09", OCT)), "duplicate version '2026-09'");
  }

  @Test
  void effectiveDatesMustIncrease() {
    assertInvalid(
        props(ruleSet("2026-09", OCT), ruleSet("2026-10", JAN)),
        "effective-from must be later than the previous rule set");
    assertInvalid(
        props(ruleSet("2026-09", JAN), ruleSet("2026-10", JAN)),
        "effective-from must be later than the previous rule set");
  }

  @Test
  void missingZeroTier() {
    List<FineTierConfig> noZero =
        List.of(new FineTierConfig(d("10.00"), d("1000.00")), new FineTierConfig(d("20.00"), d("2000.00")));
    assertInvalid(
        props(new RuleSetConfig("2026-09", JAN, "INR", d("80.00"), zones(), noZero)),
        "need a tier with over-by-kph 0");
  }

  @Test
  void decreasingAmounts() {
    List<FineTierConfig> decreasing =
        List.of(new FineTierConfig(d("0.00"), d("2000.00")), new FineTierConfig(d("20.00"), d("1000.00")));
    assertInvalid(
        props(new RuleSetConfig("2026-09", JAN, "INR", d("80.00"), zones(), decreasing)),
        "fine for +20.00 is lower than the fine for +0.00");
  }

  @Test
  void duplicateZoneAfterCanonicalising() {
    List<ZoneConfig> dup =
        List.of(new ZoneConfig("school-zone", d("30.00")), new ZoneConfig("SCHOOL-ZONE", d("25.00")));
    assertInvalid(
        props(new RuleSetConfig("2026-09", JAN, "INR", d("80.00"), dup, tiers())),
        "duplicate zone id 'SCHOOL-ZONE'");
  }

  @Test
  void missingZoneId() {
    List<ZoneConfig> noId = List.of(new ZoneConfig(null, d("30.00")));
    assertInvalid(
        props(new RuleSetConfig("2026-09", JAN, "INR", d("80.00"), noId, tiers())),
        "invalid zone id 'null'");
  }

  @Test
  void badCurrency() {
    assertInvalid(
        props(new RuleSetConfig("2026-09", JAN, "RUPEES", d("80.00"), zones(), tiers())),
        "invalid currency 'RUPEES'");
  }

  @Test
  void scaleAboveTwoDecimals() {
    List<ZoneConfig> fineLimit = List.of(new ZoneConfig("SCHOOL-ZONE", d("30.005")));
    assertInvalid(
        props(new RuleSetConfig("2026-09", JAN, "INR", d("80.00"), fineLimit, tiers())),
        "limit for zone 'SCHOOL-ZONE' must be > 0, <= 300, with at most 2 decimals");

    List<FineTierConfig> fineTier =
        List.of(new FineTierConfig(d("0.00"), d("1000.00")), new FineTierConfig(d("20.001"), d("2000.00")));
    assertInvalid(
        props(new RuleSetConfig("2026-09", JAN, "INR", d("80.00"), zones(), fineTier)),
        "over-by-kph 20.001 has more than 2 decimals");

    List<FineTierConfig> fineAmount = List.of(new FineTierConfig(d("0.00"), d("1000.005")));
    assertInvalid(
        props(new RuleSetConfig("2026-09", JAN, "INR", d("80.00"), zones(), fineAmount)),
        "amount 1000.005 has more than 2 decimals");
  }

  @Test
  void mixedCurrencies() {
    assertInvalid(
        props(
            ruleSet("2026-09", JAN),
            new RuleSetConfig("2026-10", OCT, "USD", d("80.00"), zones(), tiers())),
        "all rule sets must use the same currency, found [INR, USD]");
  }

  @Test
  void clockSkewAboveTheDatabaseCheck() {
    RuleSetProperties props =
        new RuleSetProperties(
            "Asia/Kolkata", Duration.ofDays(90), Duration.ofMinutes(10), List.of(ruleSet("2026-09", JAN)));
    assertInvalid(props, "max-clock-skew must be at most PT5M");
  }

  @Test
  void invalidTimeZone() {
    RuleSetProperties props =
        new RuleSetProperties(
            "Mars/Olympus", Duration.ofDays(90), Duration.ofMinutes(5), List.of(ruleSet("2026-09", JAN)));
    assertInvalid(props, "invalid jurisdiction-time-zone 'Mars/Olympus'");
  }

  @Test
  void nullOverByKphIsAnErrorNotACrash() {
    List<FineTierConfig> nullTier =
        List.of(new FineTierConfig(d("0.00"), d("1000.00")), new FineTierConfig(null, d("2000.00")));
    assertInvalid(
        props(new RuleSetConfig("2026-09", JAN, "INR", d("80.00"), zones(), nullTier)),
        "over-by-kph must be set and >= 0");
  }

  @Test
  void reservedLegacyVersion() {
    assertInvalid(props(ruleSet("legacy-unverified", JAN)), "reserved for migrated rows");
  }

  @Test
  void allErrorsInOneMessage() {
    RuleSetProperties props =
        new RuleSetProperties(
            "Mars/Olympus",
            Duration.ofDays(90),
            Duration.ofMinutes(5),
            List.of(
                ruleSet("2026-09", OCT),
                new RuleSetConfig("2026-09", JAN, "RUPEES", d("80.00"), zones(), tiers())));

    assertThatThrownBy(() -> RuleSetValidator.validate(props))
        .hasMessageContaining("invalid jurisdiction-time-zone")
        .hasMessageContaining("duplicate version '2026-09'")
        .hasMessageContaining("effective-from must be later")
        .hasMessageContaining("invalid currency 'RUPEES'");
  }

  @Test
  void startupFailsWithTheCombinedMessage() {
    new ApplicationContextRunner()
        .withUserConfiguration(RulesConfig.class)
        .withPropertyValues(
            "traffic.rule-sets[0].version=2026-09",
            "traffic.rule-sets[0].effective-from=2026-01-01",
            "traffic.rule-sets[0].currency=XYZ",
            "traffic.rule-sets[0].fine-tiers[0].over-by-kph=10",
            "traffic.rule-sets[0].fine-tiers[0].amount=1000")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .hasMessageContaining("invalid currency 'XYZ'")
                  .hasMessageContaining("need a tier with over-by-kph 0");
            });
  }

  @Test
  void shippedConfigStartsUp() {
    new ApplicationContextRunner()
        .withUserConfiguration(RulesConfig.class)
        .withInitializer(
            ctx -> {
              try {
                new YamlPropertySourceLoader()
                    .load("app", new ClassPathResource("application.yml"))
                    .forEach(ctx.getEnvironment().getPropertySources()::addLast);
              } catch (IOException e) {
                throw new IllegalStateException(e);
              }
            })
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(RuleSetCatalog.class).getRuleSets()).hasSize(2);
            });
  }
}
