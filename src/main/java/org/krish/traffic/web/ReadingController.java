package org.krish.traffic.web;

import jakarta.validation.Valid;
import java.net.URI;
import org.krish.traffic.citation.CitationService;
import org.krish.traffic.web.dto.EvaluationResponse;
import org.krish.traffic.web.dto.ReadingRequest;
import org.krish.traffic.web.mapper.CitationMapper;
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
        citationService.submit(
            request.vehicleId(),
            request.zone(),
            request.speedKph(),
            request.isEmergency(),
            request.observedAt());
    EvaluationResponse response = CitationMapper.toResponse(result);
    if (response.citation() != null) {
      URI location = URI.create("/api/citations/" + response.citation().id());
      return ResponseEntity.created(location).body(response);
    }
    return ResponseEntity.ok(response);
  }
}
