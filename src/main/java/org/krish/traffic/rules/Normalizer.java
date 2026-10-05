package org.krish.traffic.rules;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Normalizer {

  private static final Pattern CANONICAL_ZONE_PATTERN = Pattern.compile("^[A-Z0-9-]{2,50}$");
  private static final Pattern CANONICAL_VEHICLE_PATTERN = Pattern.compile("^[A-Z0-9]{2,20}$");

  private Normalizer() {}

  public static String canonicalizeZone(String zone) {
    if (zone == null) {
      return null;
    }
    String normalized = zone.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_]+", "-");
    if (!CANONICAL_ZONE_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Zone must be 2–50 letters, digits or hyphens");
    }
    return normalized;
  }

  public static String canonicalizeVehicleId(String vehicleId) {
    if (vehicleId == null) {
      return null;
    }
    String normalized = vehicleId.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "");
    if (!CANONICAL_VEHICLE_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Vehicle ID must be 2–20 alphanumeric characters");
    }
    return normalized;
  }
}
