package org.krish.traffic.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CitationResponse(
    Long id,
    String vehicleId,
    String zone,
    BigDecimal speedKph,
    BigDecimal speedLimitKph,
    BigDecimal excessKph,
    BigDecimal fineAmount,
    String currency,
    String ruleSetVersion,
    Instant observedAt,
    Instant recordedAt,
    String reason) {}
