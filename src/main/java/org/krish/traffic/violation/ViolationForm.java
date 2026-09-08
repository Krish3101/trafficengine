package org.krish.traffic.violation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ViolationForm {

  @NotBlank(message = "Vehicle ID is required")
  @Size(min = 2, max = 20, message = "Vehicle ID must be between 2 and 20 characters")
  @Pattern(
      regexp = "^[a-zA-Z0-9- ]+$",
      message = "Vehicle ID must contain only alphanumeric characters, spaces, or hyphens")
  private String vehicleId;

  @NotNull(message = "Speed is required")
  @DecimalMin(value = "0.0", message = "Speed cannot be negative")
  @DecimalMax(value = "300.0", message = "Speed cannot exceed 300 km/h")
  private Double speed;

  @NotBlank(message = "Zone is required")
  @Size(min = 2, max = 50, message = "Zone must be between 2 and 50 characters")
  @Pattern(
      regexp = "^[a-zA-Z0-9-_ ]+$",
      message = "Zone must contain only alphanumeric characters, spaces, hyphens, or underscores")
  private String zone;

  private boolean emergency;

  // Getters and Setters
  public String getVehicleId() {
    return vehicleId;
  }

  public void setVehicleId(String vehicleId) {
    this.vehicleId = vehicleId;
  }

  public Double getSpeed() {
    return speed;
  }

  public void setSpeed(Double speed) {
    this.speed = speed;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public boolean isEmergency() {
    return emergency;
  }

  public void setEmergency(boolean emergency) {
    this.emergency = emergency;
  }
}
