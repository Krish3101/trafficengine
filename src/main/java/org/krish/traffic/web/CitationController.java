package org.krish.traffic.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.krish.traffic.citation.CitationService;
import org.krish.traffic.web.dto.CitationResponse;
import org.krish.traffic.web.dto.SummaryResponse;
import org.krish.traffic.web.mapper.CitationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
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
      @RequestParam(required = false) String zone,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) Long before) {
    if (limit < 1 || limit > 200) {
      throw new IllegalArgumentException("Limit must be between 1 and 200");
    }

    List<CitationResponse> citations =
        citationService.list(zone, limit, before).stream().map(CitationMapper::toResponse).toList();
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();

    if (citations.size() == limit) {
      Long nextBefore = citations.getLast().id();
      StringBuilder linkHeader =
          new StringBuilder("</api/citations?limit=").append(limit).append("&before=").append(nextBefore);
      if (zone != null && !zone.isBlank()) {
        linkHeader.append("&zone=").append(URLEncoder.encode(zone.trim(), StandardCharsets.UTF_8));
      }
      linkHeader.append(">; rel=\"next\"");
      builder.header(HttpHeaders.LINK, linkHeader.toString());
    }

    return builder.body(citations);
  }

  @GetMapping("/api/citations/{id}")
  public ResponseEntity<CitationResponse> getCitationById(@PathVariable Long id) {
    return citationService
        .find(id)
        .map(c -> ResponseEntity.ok(CitationMapper.toResponse(c)))
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No citation with id " + id));
  }

  @GetMapping("/api/analytics/summary")
  public ResponseEntity<SummaryResponse> getSummary() {
    return ResponseEntity.ok(CitationMapper.toResponse(citationService.summary()));
  }
}
