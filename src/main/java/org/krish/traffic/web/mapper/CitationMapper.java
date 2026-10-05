package org.krish.traffic.web.mapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.krish.traffic.citation.Citation;
import org.krish.traffic.citation.CitationService;
import org.krish.traffic.citation.ZoneCount;
import org.krish.traffic.rules.Evaluation;
import org.krish.traffic.rules.Outcome;
import org.krish.traffic.rules.Reasons;
import org.krish.traffic.web.dto.CitationResponse;
import org.krish.traffic.web.dto.EvaluationResponse;
import org.krish.traffic.web.dto.SummaryResponse;
import org.krish.traffic.web.dto.ZoneSummary;

public final class CitationMapper {

  private CitationMapper() {}

  public static EvaluationResponse toResponse(CitationService.Result result) {
    Evaluation e = result.evaluation();
    return new EvaluationResponse(
        e.outcome(),
        result.vehicleId(),
        e.zone(),
        e.speedKph(),
        e.speedLimitKph(),
        e.excessKph(),
        e.currency(),
        e.defaultLimit(),
        result.citation() == null ? null : toResponse(result.citation()),
        e.reason());
  }

  public static CitationResponse toResponse(Citation citation) {
    return new CitationResponse(
        citation.getId(),
        citation.getVehicleId(),
        citation.getZone(),
        citation.getSpeedKph(),
        citation.getSpeedLimitKph(),
        citation.getExcessKph(),
        citation.getFineAmount(),
        citation.getCurrency(),
        citation.getRuleSetVersion(),
        citation.getObservedAt(),
        citation.getRecordedAt(),
        renderReason(citation));
  }

  public static SummaryResponse toResponse(CitationService.Summary summary) {
    List<ZoneCount> counts = summary.zones();
    long total = counts.stream().mapToLong(ZoneCount::getCount).sum();
    BigDecimal fines =
        counts.stream().map(ZoneCount::getFineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    List<ZoneSummary> zones =
        counts.stream()
            .sorted(
                Comparator.comparingLong(ZoneCount::getCount)
                    .reversed()
                    .thenComparing(ZoneCount::getZone))
            .map(zc -> new ZoneSummary(zc.getZone(), zc.getCount(), zc.getFineTotal()))
            .toList();
    return new SummaryResponse(total, fines, summary.currency(), zones);
  }

  public static String renderReason(Citation c) {
    return Reasons.explain(
        c.getRuleSetVersion(),
        c.getZone(),
        c.getSpeedLimitKph(),
        c.getSpeedKph(),
        Outcome.VIOLATION,
        c.getTierOverByKph(),
        c.getFineAmount(),
        c.getCurrency());
  }
}
