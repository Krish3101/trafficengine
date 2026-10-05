package org.krish.traffic.citation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.krish.traffic.config.RuleSetProperties;
import org.krish.traffic.rules.Evaluation;
import org.krish.traffic.rules.Normalizer;
import org.krish.traffic.rules.Outcome;
import org.krish.traffic.rules.SpeedRuleEngine;
import org.krish.traffic.rules.UnenforceableReadingException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CitationService {

  // citation is null unless the outcome is VIOLATION
  public record Result(String vehicleId, Evaluation evaluation, Citation citation) {}

  // all rule sets use one currency, so the totals are per zone only
  public record Summary(List<ZoneCount> zones, String currency) {}

  private final SpeedRuleEngine speedRuleEngine;
  private final CitationRepository citationRepository;
  private final RuleSetProperties properties;
  private final Clock clock;

  public CitationService(
      SpeedRuleEngine speedRuleEngine,
      CitationRepository citationRepository,
      RuleSetProperties properties,
      Clock clock) {
    this.speedRuleEngine = speedRuleEngine;
    this.citationRepository = citationRepository;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public Result submit(
      String rawVehicleId, String rawZone, BigDecimal speedKph, boolean emergency,
      Instant observedAt) {
    String vehicleId = Normalizer.canonicalizeVehicleId(rawVehicleId);
    String zone = Normalizer.canonicalizeZone(rawZone);
    // Postgres stores microseconds, so truncate here to keep the POST and GET bodies equal
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    Instant observed = observedAt != null ? observedAt.truncatedTo(ChronoUnit.MICROS) : now;

    if (observed.isAfter(now.plus(properties.maxClockSkew()))) {
      throw new UnenforceableReadingException(
          "observedAt cannot be more than "
              + properties.maxClockSkew().toMinutes()
              + " minutes in the future");
    }
    if (observed.isBefore(now.minus(properties.maxReadingAge()))) {
      throw new UnenforceableReadingException(
          "observedAt is older than " + properties.maxReadingAge().toDays() + " days");
    }

    Evaluation evaluation = speedRuleEngine.evaluate(zone, speedKph, emergency, observed);
    if (evaluation.outcome() != Outcome.VIOLATION) {
      return new Result(vehicleId, evaluation, null);
    }

    Citation citation =
        new Citation(
            null,
            vehicleId,
            zone,
            evaluation.speedKph(),
            evaluation.speedLimitKph(),
            evaluation.excessKph(),
            evaluation.tierOverByKph(),
            evaluation.fineAmount(),
            evaluation.currency(),
            evaluation.ruleSetVersion(),
            observed,
            now);
    return new Result(vehicleId, evaluation, citationRepository.save(citation));
  }

  // newest first; a blank zone means all zones
  @Transactional(readOnly = true)
  public List<Citation> list(String rawZone, int limit, Long before) {
    String zone = rawZone == null || rawZone.isBlank() ? null : Normalizer.canonicalizeZone(rawZone);
    PageRequest page = PageRequest.of(0, limit);
    if (zone != null) {
      return before != null
          ? citationRepository.findByZoneAndIdLessThanOrderByIdDesc(zone, before, page)
          : citationRepository.findByZoneOrderByIdDesc(zone, page);
    }
    return before != null
        ? citationRepository.findByIdLessThanOrderByIdDesc(before, page)
        : citationRepository.findAllByOrderByIdDesc(page);
  }

  @Transactional(readOnly = true)
  public Optional<Citation> find(Long id) {
    return citationRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public Summary summary() {
    String currency = properties.ruleSets().getLast().currency();
    return new Summary(citationRepository.findZoneCounts(), currency);
  }
}
