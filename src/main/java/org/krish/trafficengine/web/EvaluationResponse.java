package org.krish.trafficengine.web;

import java.math.BigDecimal;
import org.krish.trafficengine.citation.CitationService;
import org.krish.trafficengine.rules.Evaluation;

public record EvaluationResponse(
    Evaluation.Outcome outcome,
    String vehicleId,
    String zone,
    BigDecimal speedKph,
    BigDecimal speedLimitKph,
    BigDecimal excessKph,
    CitationResponse citation,
    String reason) {

  public static EvaluationResponse from(CitationService.Result result) {
    Evaluation e = result.evaluation();
    return new EvaluationResponse(
        e.outcome(),
        result.vehicleId(),
        e.zone(),
        e.speedKph(),
        e.speedLimitKph(),
        e.excessKph(),
        result.citation() == null ? null : CitationResponse.from(result.citation()),
        e.reason());
  }
}
