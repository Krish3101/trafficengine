package org.krish.traffic.violation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.krish.traffic.rules.Evaluation;
import org.krish.traffic.rules.Outcome;
import org.krish.traffic.rules.SpeedRuleEngine;
import org.krish.traffic.rules.TrafficRulesProperties;
import org.krish.traffic.web.dto.EvaluationResponse;
import org.krish.traffic.web.dto.ReadingRequest;
import org.krish.traffic.web.dto.SummaryResponse;
import org.krish.traffic.web.dto.ViolationResponse;
import org.krish.traffic.web.dto.ZoneSummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViolationService {

  private final SpeedRuleEngine speedRuleEngine;
  private final ViolationRepository violationRepository;
  private final TrafficRulesProperties properties;

  public ViolationService(
      SpeedRuleEngine speedRuleEngine,
      ViolationRepository violationRepository,
      TrafficRulesProperties properties) {
    this.speedRuleEngine = speedRuleEngine;
    this.violationRepository = violationRepository;
    this.properties = properties;
  }

  @Transactional
  public EvaluationResponse submitReading(ReadingRequest request) {
    String normalisedVehicleId = request.vehicleId().trim().toUpperCase(Locale.ROOT);
    String normalisedZone = request.zone().trim().toUpperCase(Locale.ROOT);

    Evaluation evaluation =
        speedRuleEngine.evaluate(normalisedZone, request.speedKph(), request.isEmergency());

    if (evaluation.outcome() == Outcome.VIOLATION) {
      Violation violation =
          new Violation(
              null,
              normalisedVehicleId,
              normalisedZone,
              request.speedKph(),
              evaluation.speedLimitKph(),
              evaluation.fineAmount(),
              Instant.now());
      Violation saved = violationRepository.save(violation);
      return new EvaluationResponse(
          Outcome.VIOLATION,
          normalisedVehicleId,
          normalisedZone,
          request.speedKph(),
          evaluation.speedLimitKph(),
          evaluation.excessKph(),
          properties.getCurrency(),
          ViolationResponse.from(saved));
    }

    return new EvaluationResponse(
        evaluation.outcome(),
        normalisedVehicleId,
        normalisedZone,
        request.speedKph(),
        evaluation.speedLimitKph(),
        evaluation.excessKph(),
        properties.getCurrency(),
        null);
  }

  @Transactional(readOnly = true)
  public List<ViolationResponse> getRecentViolations(String zone, int limit) {
    int clampedLimit = (limit <= 0) ? 50 : Math.min(limit, 200);
    PageRequest pageable = PageRequest.of(0, clampedLimit);

    List<Violation> violations;
    if (zone != null && !zone.isBlank()) {
      violations =
          violationRepository.findByZoneIgnoreCaseOrderByRecordedAtDescIdDesc(
              zone.trim(), pageable);
    } else {
      violations = violationRepository.findAllByOrderByRecordedAtDescIdDesc(pageable);
    }

    return violations.stream().map(ViolationResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public SummaryResponse getSummary() {
    List<ZoneCount> zoneCounts = violationRepository.findZoneCounts();

    long totalViolations = zoneCounts.stream().mapToLong(ZoneCount::getCount).sum();
    long totalFineAmount = zoneCounts.stream().mapToLong(ZoneCount::getFineTotal).sum();

    List<ZoneSummary> zones =
        zoneCounts.stream()
            .sorted(
                Comparator.comparingLong(ZoneCount::getCount)
                    .reversed()
                    .thenComparing(ZoneCount::getZone))
            .map(zc -> new ZoneSummary(zc.getZone(), zc.getCount(), zc.getFineTotal()))
            .toList();

    return new SummaryResponse(totalViolations, totalFineAmount, properties.getCurrency(), zones);
  }
}
