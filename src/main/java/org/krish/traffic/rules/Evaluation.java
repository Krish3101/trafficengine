package org.krish.traffic.rules;

public record Evaluation(Outcome outcome, double speedLimitKph, double excessKph, int fineAmount) {}
