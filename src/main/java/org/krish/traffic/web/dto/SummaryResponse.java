package org.krish.traffic.web.dto;

import java.util.List;

public record SummaryResponse(
    long totalViolations, long totalFineAmount, String currency, List<ZoneSummary> zones) {}
