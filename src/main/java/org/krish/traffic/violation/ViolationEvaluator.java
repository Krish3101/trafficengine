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

  public Optional<ViolationRecord> evaluate(VehicleEvent event) {
    if (event == null
        || event.speed <= properties.getSpeedThreshold()
        || event.isEmergencyVehicle) {
      return Optional.empty();
    }
    String vehicleId = Optional.ofNullable(event.vehicleId).orElse("UNKNOWN");
    String zone = Optional.ofNullable(event.zone).orElse("UNKNOWN_ZONE");
    int fine = calculateFine(event.speed);
    return Optional.of(new ViolationRecord(vehicleId, event.speed, zone, fine));
  }

  public Optional<TrafficViolation> evaluateAndRecord(ViolationForm form) {
    VehicleEvent event =
        new VehicleEvent(form.getVehicleId(), form.getSpeed(), form.getZone(), form.isEmergency());
    return evaluate(event)
        .map(
            v -> {
              TrafficViolation db = new TrafficViolation();
              db.setVehicleId(v.vehicleId);
              db.setSpeed(v.speed);
              db.setZone(v.zone);
              db.setFine(v.fine);
              return repository.save(db);
            });
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

  public static class VehicleEvent {
    public String vehicleId;
    public double speed;
    public String zone;
    public boolean isEmergencyVehicle;

    public VehicleEvent(String vehicleId, double speed, String zone, boolean isEmergencyVehicle) {
      this.vehicleId = vehicleId;
      this.speed = speed;
      this.zone = zone;
      this.isEmergencyVehicle = isEmergencyVehicle;
    }
  }

  public static class ViolationRecord {
    public String vehicleId;
    public double speed;
    public String zone;
    public int fine;

    public ViolationRecord(String vehicleId, double speed, String zone, int fine) {
      this.vehicleId = vehicleId;
      this.speed = speed;
      this.zone = zone;
      this.fine = fine;
    }
  }
}
