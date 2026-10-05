package org.krish.traffic.web.dto;

import java.math.BigDecimal;

public record ZoneSummary(String zone, long citations, BigDecimal fineAmount) {}
