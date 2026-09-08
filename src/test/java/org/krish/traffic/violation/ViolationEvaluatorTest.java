package org.krish.traffic.violation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.krish.traffic.config.TrafficRulesProperties;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ViolationEvaluatorTest {

  @Mock private TrafficViolationRepository repository;

  private TrafficRulesProperties properties;
  private ViolationEvaluator evaluator;

  @BeforeEach
  void setUp() {
    properties = new TrafficRulesProperties();
    properties.setSpeedThreshold(80.0);

    List<TrafficRulesProperties.FineTier> tiers = new ArrayList<>();

    TrafficRulesProperties.FineTier tier1 = new TrafficRulesProperties.FineTier();
    tier1.setThreshold(120.0);
    tier1.setAmount(5000);
    tiers.add(tier1);

    TrafficRulesProperties.FineTier tier2 = new TrafficRulesProperties.FineTier();
    tier2.setThreshold(100.0);
    tier2.setAmount(2000);
    tiers.add(tier2);

    TrafficRulesProperties.FineTier tier3 = new TrafficRulesProperties.FineTier();
    tier3.setThreshold(80.0);
    tier3.setAmount(1000);
    tiers.add(tier3);

    properties.setFineTiers(tiers);

    evaluator = new ViolationEvaluator(repository, properties);
  }

  @Test
  void testSpeedAboveThresholdNonEmergencyCreatesViolationWithCorrectFine() {
    ViolationEvaluator.VehicleEvent event =
        new ViolationEvaluator.VehicleEvent("KA01AB1234", 110.0, "Zone-A", false);
    Optional<ViolationEvaluator.ViolationRecord> recordOpt = evaluator.evaluate(event);

    assertTrue(recordOpt.isPresent());
    ViolationEvaluator.ViolationRecord record = recordOpt.get();
    assertEquals("KA01AB1234", record.vehicleId);
    assertEquals(110.0, record.speed);
    assertEquals("Zone-A", record.zone);
    assertEquals(2000, record.fine);
  }

  @Test
  void testSpeedExactlyEqualToThresholdNoViolation() {
    ViolationEvaluator.VehicleEvent event =
        new ViolationEvaluator.VehicleEvent("KA01AB1234", 80.0, "Zone-A", false);
    Optional<ViolationEvaluator.ViolationRecord> recordOpt = evaluator.evaluate(event);

    assertFalse(recordOpt.isPresent());
  }

  @Test
  void testSpeedAboveThresholdEmergencyVehicleNoViolation() {
    ViolationEvaluator.VehicleEvent event =
        new ViolationEvaluator.VehicleEvent("AMB01", 140.0, "Zone-A", true);
    Optional<ViolationEvaluator.ViolationRecord> recordOpt = evaluator.evaluate(event);

    assertFalse(recordOpt.isPresent());
  }

  @Test
  void testNullVehicleIdAndZoneDefaultToUnknown() {
    ViolationEvaluator.VehicleEvent event =
        new ViolationEvaluator.VehicleEvent(null, 95.0, null, false);
    Optional<ViolationEvaluator.ViolationRecord> recordOpt = evaluator.evaluate(event);

    assertTrue(recordOpt.isPresent());
    ViolationEvaluator.ViolationRecord record = recordOpt.get();
    assertEquals("UNKNOWN", record.vehicleId);
    assertEquals("UNKNOWN_ZONE", record.zone);
    assertEquals(1000, record.fine);
  }

  @Test
  void testNoFineTiersConfiguredFallsBackToDefaultFine() {
    properties.setFineTiers(null);
    ViolationEvaluator.VehicleEvent event =
        new ViolationEvaluator.VehicleEvent("KA01AB1234", 130.0, "Zone-A", false);
    Optional<ViolationEvaluator.ViolationRecord> recordOpt = evaluator.evaluate(event);

    assertTrue(recordOpt.isPresent());
    assertEquals(1000, recordOpt.get().fine);

    properties.setFineTiers(List.of());
    recordOpt = evaluator.evaluate(event);
    assertTrue(recordOpt.isPresent());
    assertEquals(1000, recordOpt.get().fine);
  }

  @Test
  void testSpeedOnTierBoundaryDoesNotMatchHigherTier() {
    ViolationEvaluator.VehicleEvent event =
        new ViolationEvaluator.VehicleEvent("KA01AB1234", 100.0, "Zone-A", false);
    Optional<ViolationEvaluator.ViolationRecord> recordOpt = evaluator.evaluate(event);

    assertTrue(recordOpt.isPresent());
    assertEquals(1000, recordOpt.get().fine);
  }

  @Test
  void testEvaluateNullEventReturnsEmpty() {
    Optional<ViolationEvaluator.ViolationRecord> recordOpt = evaluator.evaluate(null);
    assertFalse(recordOpt.isPresent());
  }

  @Test
  void testEvaluateAndRecordSavesWhenViolationDetected() {
    ViolationForm form = new ViolationForm();
    form.setVehicleId("KA01AB1234");
    form.setSpeed(110.0);
    form.setZone("Zone-B");
    form.setEmergency(false);

    TrafficViolation savedEntity = new TrafficViolation();
    savedEntity.setId(1L);
    savedEntity.setVehicleId("KA01AB1234");
    savedEntity.setSpeed(110.0);
    savedEntity.setZone("Zone-B");
    savedEntity.setFine(2000);

    when(repository.save(any(TrafficViolation.class))).thenReturn(savedEntity);

    Optional<TrafficViolation> result = evaluator.evaluateAndRecord(form);

    assertTrue(result.isPresent());
    assertEquals(1L, result.get().getId());
    assertEquals(2000, result.get().getFine());

    ArgumentCaptor<TrafficViolation> captor = ArgumentCaptor.forClass(TrafficViolation.class);
    verify(repository).save(captor.capture());
    TrafficViolation captured = captor.getValue();
    assertEquals("KA01AB1234", captured.getVehicleId());
    assertEquals(110.0, captured.getSpeed());
    assertEquals("Zone-B", captured.getZone());
    assertEquals(2000, captured.getFine());
  }

  @Test
  void testEvaluateAndRecordDoesNotSaveWhenNoViolation() {
    ViolationForm form = new ViolationForm();
    form.setVehicleId("KA01AB1234");
    form.setSpeed(75.0);
    form.setZone("Zone-B");
    form.setEmergency(false);

    Optional<TrafficViolation> result = evaluator.evaluateAndRecord(form);

    assertFalse(result.isPresent());
    verify(repository, never()).save(any());
  }
}
