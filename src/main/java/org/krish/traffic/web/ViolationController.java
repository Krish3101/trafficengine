package org.krish.traffic.web;

import jakarta.validation.Valid;
import java.util.List;
import org.krish.traffic.rules.Outcome;
import org.krish.traffic.violation.ViolationService;
import org.krish.traffic.web.dto.EvaluationResponse;
import org.krish.traffic.web.dto.ReadingRequest;
import org.krish.traffic.web.dto.SummaryResponse;
import org.krish.traffic.web.dto.ViolationResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ViolationController {

  private final ViolationService violationService;

  public ViolationController(ViolationService violationService) {
    this.violationService = violationService;
  }

  @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> index() {
    return ResponseEntity.ok(new ClassPathResource("static/index.html"));
  }

  @PostMapping("/api/readings")
  public ResponseEntity<EvaluationResponse> submitReading(
      @Valid @RequestBody ReadingRequest request) {
    EvaluationResponse response = violationService.submitReading(request);
    if (response.outcome() == Outcome.VIOLATION) {
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/violations")
  public ResponseEntity<List<ViolationResponse>> getRecentViolations(
      @RequestParam(required = false) String zone, @RequestParam(defaultValue = "50") int limit) {
    List<ViolationResponse> violations = violationService.getRecentViolations(zone, limit);
    return ResponseEntity.ok(violations);
  }

  @GetMapping("/api/analytics/summary")
  public ResponseEntity<SummaryResponse> getSummary() {
    SummaryResponse summary = violationService.getSummary();
    return ResponseEntity.ok(summary);
  }
}
