package org.krish.traffic.violation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "violations",
    indexes = {@Index(name = "idx_violations_zone", columnList = "zone")})
public class Violation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "vehicle_id", length = 20, nullable = false)
  private String vehicleId;

  @Column(name = "zone", length = 50, nullable = false)
  private String zone;

  @Column(name = "speed_kph", nullable = false)
  private double speedKph;

  @Column(name = "speed_limit_kph", nullable = false)
  private double speedLimitKph;

  @Column(name = "fine_amount", nullable = false)
  private int fineAmount;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  protected Violation() {
    // Required by JPA
  }

  public Violation(
      Long id,
      String vehicleId,
      String zone,
      double speedKph,
      double speedLimitKph,
      int fineAmount,
      Instant recordedAt) {
    this.id = id;
    this.vehicleId = vehicleId;
    this.zone = zone;
    this.speedKph = speedKph;
    this.speedLimitKph = speedLimitKph;
    this.fineAmount = fineAmount;
    this.recordedAt = recordedAt;
  }

  public Long getId() {
    return id;
  }

  public String getVehicleId() {
    return vehicleId;
  }

  public String getZone() {
    return zone;
  }

  public double getSpeedKph() {
    return speedKph;
  }

  public double getSpeedLimitKph() {
    return speedLimitKph;
  }

  public int getFineAmount() {
    return fineAmount;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
