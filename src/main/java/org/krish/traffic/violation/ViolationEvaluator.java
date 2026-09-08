package org.krish.traffic.violation;

import java.util.*;
import org.krish.traffic.config.TrafficRulesProperties;
import org.springframework.stereotype.Service;

@Service
public class ViolationEvaluator {

  private final TrafficViolationRepository repository;
  private final TrafficRulesProperties properties;

  public ViolationEvaluator(
      TrafficViolationRepository repository, TrafficRulesProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  public Optional<TrafficViolation> evaluateAndRecord(ViolationForm form) {
    if (form == null || form.getSpeed() <= properties.getSpeedThreshold() || form.isEmergency()) {
      return Optional.empty();
    }

    String vehicleId = Optional.ofNullable(form.getVehicleId()).orElse("UNKNOWN");
    String zone = Optional.ofNullable(form.getZone()).orElse("UNKNOWN_ZONE");

    TrafficViolation db = new TrafficViolation();
    db.setVehicleId(vehicleId);
    db.setSpeed(form.getSpeed());
    db.setZone(zone);
    db.setFine(calculateFine(form.getSpeed()));

    return Optional.of(repository.save(db));
  }

  private int calculateFine(double speed) {
    if (properties.getFineTiers() == null || properties.getFineTiers().isEmpty()) {
      return 1000;
    }
    return properties.getFineTiers().stream()
        .sorted((t1, t2) -> Double.compare(t2.getThreshold(), t1.getThreshold()))
        .filter(tier -> speed > tier.getThreshold())
        .findFirst()
        .map(TrafficRulesProperties.FineTier::getAmount)
        .orElse(1000);
  }

  public long getTotalViolations() {
    return repository.count();
  }

  public long getTotalFinesCollected() {
    Long total = repository.sumAllFines();
    return total == null ? 0L : total;
  }

  public Map<String, Long> getZoneWiseAnalytics() {
    List<Object[]> results = repository.countViolationsByZone();
    Map<String, Long> map = new HashMap<>();
    for (Object[] result : results) {
      map.put((String) result[0], (Long) result[1]);
    }
    return map;
  }
}
