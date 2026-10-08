package org.krish.trafficengine.web;

import java.math.BigDecimal;
import java.time.Instant;
import org.krish.trafficengine.citation.Citation;

public record CitationResponse(
    Long id,
    String vehicleId,
    String zone,
    BigDecimal speedKph,
    BigDecimal speedLimitKph,
    BigDecimal excessKph,
    BigDecimal fineAmount,
    String ruleSetVersion,
    Instant observedAt,
    Instant recordedAt,
    String reason) {

  public static CitationResponse from(Citation c) {
    return new CitationResponse(
        c.getId(),
        c.getVehicleId(),
        c.getZone(),
        c.getSpeedKph(),
        c.getSpeedLimitKph(),
        c.getExcessKph(),
        c.getFineAmount(),
        c.getRuleSetVersion(),
        c.getObservedAt(),
        c.getRecordedAt(),
        c.getReason());
  }
}
