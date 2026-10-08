package org.krish.trafficengine.citation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.krish.trafficengine.rules.Evaluation;
import org.krish.trafficengine.rules.Normalizer;
import org.krish.trafficengine.rules.SpeedRuleEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CitationService {

  // citation is null unless the outcome is VIOLATION
  public record Result(String vehicleId, Evaluation evaluation, Citation citation) {}

  private final SpeedRuleEngine speedRuleEngine;
  private final CitationRepository citationRepository;

  public CitationService(
      SpeedRuleEngine speedRuleEngine,
      CitationRepository citationRepository) {
    this.speedRuleEngine = speedRuleEngine;
    this.citationRepository = citationRepository;
  }

  @Transactional
  public Result recordReading(
      String rawVehicleId, String rawZone, BigDecimal speedKph, boolean emergency,
      Instant observedAt) {
    String vehicleId = Normalizer.normalizeVehicleId(rawVehicleId);
    String zone = Normalizer.normalizeZone(rawZone);
    // Postgres stores microseconds, so truncate here to keep the POST and GET bodies equal
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Instant observed = observedAt != null ? observedAt.truncatedTo(ChronoUnit.MICROS) : now;

    Evaluation evaluation = speedRuleEngine.evaluate(zone, speedKph, emergency, observed);
    if (evaluation.outcome() != Evaluation.Outcome.VIOLATION) {
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
            evaluation.fineAmount(),
            evaluation.ruleSetVersion(),
            evaluation.reason(),
            observed,
            now);
    return new Result(vehicleId, evaluation, citationRepository.save(citation));
  }

  // newest 50; a blank zone means all zones
  @Transactional(readOnly = true)
  public List<Citation> list(String rawZone) {
    String zone = rawZone == null || rawZone.isBlank() ? null : Normalizer.normalizeZone(rawZone);
    return citationRepository.findNewest50(zone);
  }

  @Transactional(readOnly = true)
  public Optional<Citation> find(Long id) {
    return citationRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public List<ZoneSummary> summary() {
    return citationRepository.findZoneSummaries();
  }
}
