package org.krish.traffic.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReadingRequest(
    @NotNull(message = "Vehicle ID must be 2–20 alphanumeric characters, spaces, or hyphens")
        @Pattern(
            regexp = "^(?=.*\\S)[A-Za-z0-9\\- ]{2,20}$",
            message = "Vehicle ID must be 2–20 alphanumeric characters, spaces, or hyphens")
        String vehicleId,
    @NotNull(message = "Zone must be 2–50 alphanumeric characters, hyphens, underscores, or spaces")
        @Pattern(
            regexp = "^(?=.*\\S)[A-Za-z0-9\\-_ ]{2,50}$",
            message = "Zone must be 2–50 alphanumeric characters, hyphens, underscores, or spaces")
        String zone,
    @NotNull(message = "Speed must be between 0 and 300 km/h")
        @DecimalMin(value = "0.0", message = "Speed must be between 0 and 300 km/h")
        @DecimalMax(value = "300.0", message = "Speed must be between 0 and 300 km/h")
        Double speedKph,
    Boolean emergency) {

  public boolean isEmergency() {
    return Boolean.TRUE.equals(emergency);
  }
}
