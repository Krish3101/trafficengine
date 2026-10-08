package org.krish.trafficengine.web;

import java.util.List;
import org.krish.trafficengine.citation.CitationService;
import org.krish.trafficengine.citation.ZoneSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CitationController {

  private final CitationService citationService;

  public CitationController(CitationService citationService) {
    this.citationService = citationService;
  }

  @GetMapping("/api/citations")
  public ResponseEntity<List<CitationResponse>> getCitations(
      @RequestParam(required = false) String zone) {
    return ResponseEntity.ok(
        citationService.list(zone).stream().map(CitationResponse::from).toList());
  }

  @GetMapping("/api/citations/{id}")
  public ResponseEntity<CitationResponse> getCitationById(@PathVariable Long id) {
    return citationService
        .find(id)
        .map(c -> ResponseEntity.ok(CitationResponse.from(c)))
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No citation with id " + id));
  }

  @GetMapping("/api/analytics/summary")
  public ResponseEntity<List<ZoneSummary>> getSummary() {
    return ResponseEntity.ok(citationService.summary());
  }
}
