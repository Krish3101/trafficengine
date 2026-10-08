package org.krish.trafficengine.rules;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Normalizer {

  private static final Pattern ZONE_PATTERN = Pattern.compile("^[A-Z0-9-]{2,50}$");
  private static final Pattern VEHICLE_PATTERN = Pattern.compile("^[A-Z0-9]{2,20}$");

  private Normalizer() {}

  public static String normalizeZone(String zone) {
    if (zone == null) {
      return null;
    }
    String normalized = zone.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_]+", "-");
    if (!ZONE_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Zone must be 2–50 letters, digits or hyphens");
    }
    return normalized;
  }

  public static String normalizeVehicleId(String vehicleId) {
    if (vehicleId == null) {
      return null;
    }
    String normalized = vehicleId.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "");
    if (!VEHICLE_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Vehicle ID must be 2–20 alphanumeric characters");
    }
    return normalized;
  }
}
