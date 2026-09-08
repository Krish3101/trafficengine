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
    ViolationForm form = new ViolationForm();
    form.setVehicleId("KA01AB1234");
    form.setSpeed(110.0);
    form.setZone("Zone-A");
    form.setEmergency(false);

    TrafficViolation mockSaved = new TrafficViolation();
    mockSaved.setVehicleId("KA01AB1234");
    mockSaved.setSpeed(110.0);
    mockSaved.setZone("Zone-A");
    mockSaved.setFine(2000);
    when(repository.save(any(TrafficViolation.class))).thenReturn(mockSaved);

    Optional<TrafficViolation> recordOpt = evaluator.evaluateAndRecord(form);

    assertTrue(recordOpt.isPresent());
    TrafficViolation record = recordOpt.get();
    assertEquals("KA01AB1234", record.getVehicleId());
    assertEquals(110.0, record.getSpeed());
    assertEquals("Zone-A", record.getZone());
    assertEquals(2000, record.getFine());
  }

  @Test
  void testSpeedExactlyEqualToThresholdNoViolation() {
    ViolationForm form = new ViolationForm();
    form.setVehicleId("KA01AB1234");
    form.setSpeed(80.0);
    form.setZone("Zone-A");
    form.setEmergency(false);
    Optional<TrafficViolation> recordOpt = evaluator.evaluateAndRecord(form);

    assertFalse(recordOpt.isPresent());
  }

  @Test
  void testSpeedAboveThresholdEmergencyVehicleNoViolation() {
    ViolationForm form = new ViolationForm();
    form.setVehicleId("AMB01");
    form.setSpeed(140.0);
    form.setZone("Zone-A");
    form.setEmergency(true);
    Optional<TrafficViolation> recordOpt = evaluator.evaluateAndRecord(form);

    assertFalse(recordOpt.isPresent());
  }

  @Test
  void testNullVehicleIdAndZoneDefaultToUnknown() {
    ViolationForm form = new ViolationForm();
    form.setVehicleId(null);
    form.setSpeed(95.0);
    form.setZone(null);
    form.setEmergency(false);

    TrafficViolation mockSaved = new TrafficViolation();
    mockSaved.setVehicleId("UNKNOWN");
    mockSaved.setZone("UNKNOWN_ZONE");
    mockSaved.setFine(1000);
    when(repository.save(any(TrafficViolation.class))).thenReturn(mockSaved);

    Optional<TrafficViolation> recordOpt = evaluator.evaluateAndRecord(form);

    assertTrue(recordOpt.isPresent());
    TrafficViolation record = recordOpt.get();
    assertEquals("UNKNOWN", record.getVehicleId());
    assertEquals("UNKNOWN_ZONE", record.getZone());
    assertEquals(1000, record.getFine());
  }

  @Test
  void testNoFineTiersConfiguredFallsBackToDefaultFine() {
    properties.setFineTiers(null);
    ViolationForm form = new ViolationForm();
    form.setVehicleId("KA01AB1234");
    form.setSpeed(130.0);
    form.setZone("Zone-A");
    form.setEmergency(false);

    TrafficViolation mockSaved = new TrafficViolation();
    mockSaved.setFine(1000);
    when(repository.save(any(TrafficViolation.class))).thenReturn(mockSaved);

    Optional<TrafficViolation> recordOpt = evaluator.evaluateAndRecord(form);

    assertTrue(recordOpt.isPresent());
    assertEquals(1000, recordOpt.get().getFine());

    properties.setFineTiers(List.of());
    recordOpt = evaluator.evaluateAndRecord(form);
    assertTrue(recordOpt.isPresent());
    assertEquals(1000, recordOpt.get().getFine());
  }

  @Test
  void testSpeedOnTierBoundaryDoesNotMatchHigherTier() {
    ViolationForm form = new ViolationForm();
    form.setVehicleId("KA01AB1234");
    form.setSpeed(100.0);
    form.setZone("Zone-A");
    form.setEmergency(false);

    TrafficViolation mockSaved = new TrafficViolation();
    mockSaved.setFine(1000);
    when(repository.save(any(TrafficViolation.class))).thenReturn(mockSaved);

    Optional<TrafficViolation> recordOpt = evaluator.evaluateAndRecord(form);

    assertTrue(recordOpt.isPresent());
    assertEquals(1000, recordOpt.get().getFine());
  }

  @Test
  void testEvaluateNullEventReturnsEmpty() {
    Optional<TrafficViolation> recordOpt = evaluator.evaluateAndRecord(null);
    assertFalse(recordOpt.isPresent());
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
