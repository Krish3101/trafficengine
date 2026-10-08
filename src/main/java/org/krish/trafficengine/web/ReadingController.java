package org.krish.trafficengine.web;

import jakarta.validation.Valid;
import java.net.URI;
import org.krish.trafficengine.citation.CitationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/readings")
public class ReadingController {

  private final CitationService citationService;

  public ReadingController(CitationService citationService) {
    this.citationService = citationService;
  }

  @PostMapping
  public ResponseEntity<EvaluationResponse> submitReading(
      @Valid @RequestBody ReadingRequest request) {
    CitationService.Result result =
        citationService.recordReading(
            request.vehicleId(),
            request.zone(),
            request.speedKph(),
            request.isEmergency(),
            request.observedAt());
    EvaluationResponse response = EvaluationResponse.from(result);
    if (response.citation() != null) {
      URI location = URI.create("/api/citations/" + response.citation().id());
      return ResponseEntity.created(location).body(response);
    }
    return ResponseEntity.ok(response);
  }
}
