package org.krish.traffic;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.*;

@Service
public class SmartTrafficSystem {

    private final TrafficViolationRepository repository;
    private final TrafficRulesProperties properties;

    public SmartTrafficSystem(TrafficViolationRepository repository, TrafficRulesProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<ViolationRecord> process(List<VehicleEvent> events) {
        return events.stream()
                .filter(Objects::nonNull)
                .filter(event -> event.speed > properties.getSpeedThreshold() && !event.isEmergencyVehicle)
                .map(event -> {
                    String vehicleId = Optional.ofNullable(event.vehicleId).orElse("UNKNOWN");
                    String zone = Optional.ofNullable(event.zone).orElse("UNKNOWN_ZONE");
                    int fine = calculateFine(event.speed);

                    return new ViolationRecord(vehicleId, event.speed, zone, fine);
                })
                .collect(Collectors.toList());
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
        public long timestamp;

        public VehicleEvent(String vehicleId, double speed, String zone, boolean isEmergencyVehicle, long timestamp) {
            this.vehicleId = vehicleId;
            this.speed = speed;
            this.zone = zone;
            this.isEmergencyVehicle = isEmergencyVehicle;
            this.timestamp = timestamp;
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