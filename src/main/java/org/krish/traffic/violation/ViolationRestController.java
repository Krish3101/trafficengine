package org.krish.traffic.violation;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/violations")
public class ViolationRestController {

  private final TrafficViolationRepository repository;
  private final ViolationEvaluator system;

  public ViolationRestController(TrafficViolationRepository repository, ViolationEvaluator system) {
    this.repository = repository;
    this.system = system;
  }

  @GetMapping
  public List<TrafficViolation> getAllViolations() {
    return repository.findAll();
  }

  @PostMapping
  public ResponseEntity<?> submitEvent(
      @Valid @RequestBody ViolationForm form, BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
      List<String> errors =
          bindingResult.getAllErrors().stream()
              .map(error -> error.getDefaultMessage())
              .sorted()
              .collect(Collectors.toList());
      return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    Optional<TrafficViolation> saved = system.evaluateAndRecord(form);
    if (saved.isPresent()) {
      return ResponseEntity.status(HttpStatus.CREATED).body(saved.get());
    } else {
      return ResponseEntity.ok("No violation detected");
    }
  }
}
