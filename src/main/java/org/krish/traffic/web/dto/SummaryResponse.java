package org.krish.traffic.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record SummaryResponse(
    long totalCitations,
    BigDecimal totalFineAmount,
    String currency,
    List<ZoneSummary> zones) {}
