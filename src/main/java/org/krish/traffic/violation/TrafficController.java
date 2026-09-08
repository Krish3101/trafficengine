package org.krish.traffic.violation;

import jakarta.validation.Valid;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class TrafficController {

  private final TrafficViolationRepository repo;
  private final ViolationEvaluator system;

  public TrafficController(TrafficViolationRepository repo, ViolationEvaluator system) {
    this.repo = repo;
    this.system = system;
  }

  @GetMapping("/")
  public String home(Model model) {
    model.addAttribute("result", "");
    model.addAttribute("form", new ViolationForm());
    populateModel(model);
    return "index";
  }

  @PostMapping("/process")
  public String process(
      @Valid @ModelAttribute("form") ViolationForm form, BindingResult bindingResult, Model model) {

    if (bindingResult.hasErrors()) {
      String errorMsg =
          bindingResult.getAllErrors().stream()
              .map(error -> error.getDefaultMessage())
              .sorted()
              .collect(Collectors.joining("; "));
      model.addAttribute("result", "Validation failed: " + errorMsg);
      model.addAttribute("form", new ViolationForm());
      populateModel(model);
      return "index";
    }

    Optional<TrafficViolation> saved = system.evaluateAndRecord(form);
    model.addAttribute(
        "result", saved.isPresent() ? "Violation Saved Successfully" : "No violation detected");
    model.addAttribute("form", new ViolationForm());

    populateModel(model);
    return "index";
  }

  private void populateModel(Model model) {
    model.addAttribute("list", repo.findAll());
    model.addAttribute("totalFines", system.getTotalFinesCollected());
    model.addAttribute("totalCount", system.getTotalViolations());
    model.addAttribute("zoneStats", system.getZoneWiseAnalytics());
  }
}
