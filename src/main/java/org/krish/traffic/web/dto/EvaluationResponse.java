package org.krish.traffic.web.dto;

import org.krish.traffic.rules.Outcome;

public record EvaluationResponse(
    Outcome outcome,
    String vehicleId,
    String zone,
    double speedKph,
    double speedLimitKph,
    double excessKph,
    String currency,
    ViolationResponse violation) {}
