package org.krish.traffic.web.dto;

import java.time.Instant;
import org.krish.traffic.violation.Violation;

public record ViolationResponse(
    Long id,
    String vehicleId,
    String zone,
    double speedKph,
    double speedLimitKph,
    int fineAmount,
    Instant recordedAt) {

  public static ViolationResponse from(Violation violation) {
    return new ViolationResponse(
        violation.getId(),
        violation.getVehicleId(),
        violation.getZone(),
        violation.getSpeedKph(),
        violation.getSpeedLimitKph(),
        violation.getFineAmount(),
        violation.getRecordedAt());
  }
}
