package org.krish.traffic;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

@Service
public class SmartTrafficSystem {

    private final TrafficViolationRepository repository;

    public SmartTrafficSystem(TrafficViolationRepository repository) {
        this.repository = repository;
    }

    public static class TrafficRules {

        public static Predicate<VehicleEvent> violationFilter = event -> event.speed > 80 && !event.isEmergencyVehicle;

        public static Function<Double, Integer> fineCalculator = speed -> {
            if (speed > 120)
                return 5000;
            else if (speed > 100)
                return 2000;
            else
                return 1000;
        };
    }

    public List<ViolationRecord> process(List<VehicleEvent> events) {
        return events.stream()
                .filter(Objects::nonNull)
                .filter(TrafficRules.violationFilter)
                .map(event -> {
                    String vehicleId = Optional.ofNullable(event.vehicleId).orElse("UNKNOWN");
                    String zone = Optional.ofNullable(event.zone).orElse("UNKNOWN_ZONE");
                    int fine = TrafficRules.fineCalculator.apply(event.speed);

                    return new ViolationRecord(vehicleId, event.speed, zone, fine);
                })
                .collect(Collectors.toList());
    }

    public long getTotalViolations() {
        return repository.count();
    }

    public int getTotalFinesCollected() {
        return repository.findAll().stream()
                .map(v -> v.fine)
                .reduce(0, Integer::sum);
    }

    public Map<String, Long> getZoneWiseAnalytics() {
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(v -> v.zone, Collectors.counting()));
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

    public final Consumer<ViolationRecord> logger = System.out::println;
    public final Supplier<VehicleEvent> defaultEventSupplier = () -> new VehicleEvent("DEFAULT", 0, "UNKNOWN", false,
            System.currentTimeMillis());
}