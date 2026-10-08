package org.krish.trafficengine.citation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "citations")
public class Citation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "vehicle_id", length = 20, nullable = false, updatable = false)
  private String vehicleId;

  @Column(name = "zone", length = 50, nullable = false, updatable = false)
  private String zone;

  @Column(name = "speed_kph", precision = 5, scale = 2, nullable = false, updatable = false)
  private BigDecimal speedKph;

  @Column(name = "speed_limit_kph", precision = 5, scale = 2, nullable = false, updatable = false)
  private BigDecimal speedLimitKph;

  @Column(name = "excess_kph", precision = 5, scale = 2, nullable = false, updatable = false)
  private BigDecimal excessKph;

  @Column(name = "fine_amount", precision = 12, scale = 2, nullable = false, updatable = false)
  private BigDecimal fineAmount;

  @Column(name = "rule_set_version", length = 32, nullable = false, updatable = false)
  private String ruleSetVersion;

  @Column(name = "reason", nullable = false, updatable = false)
  private String reason;

  @Column(name = "observed_at", nullable = false, updatable = false)
  private Instant observedAt;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  protected Citation() {
    // Required by JPA
  }

  public Citation(
      Long id,
      String vehicleId,
      String zone,
      BigDecimal speedKph,
      BigDecimal speedLimitKph,
      BigDecimal excessKph,
      BigDecimal fineAmount,
      String ruleSetVersion,
      String reason,
      Instant observedAt,
      Instant recordedAt) {
    this.id = id;
    this.vehicleId = vehicleId;
    this.zone = zone;
    this.speedKph = speedKph;
    this.speedLimitKph = speedLimitKph;
    this.excessKph = excessKph;
    this.fineAmount = fineAmount;
    this.ruleSetVersion = ruleSetVersion;
    this.reason = reason;
    this.observedAt = observedAt;
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

  public BigDecimal getSpeedKph() {
    return speedKph;
  }

  public BigDecimal getSpeedLimitKph() {
    return speedLimitKph;
  }

  public BigDecimal getExcessKph() {
    return excessKph;
  }

  public BigDecimal getFineAmount() {
    return fineAmount;
  }

  public String getRuleSetVersion() {
    return ruleSetVersion;
  }

  public String getReason() {
    return reason;
  }

  public Instant getObservedAt() {
    return observedAt;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
