package org.krish.traffic;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class TrafficController {

    private final TrafficViolationRepository repo;
    private final SmartTrafficSystem system;

    public TrafficController(TrafficViolationRepository repo, SmartTrafficSystem system) {
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
            @Valid @ModelAttribute("form") ViolationForm form,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
            model.addAttribute("result", "Validation failed: " + errorMsg);
            populateModel(model);
            return "index";
        }

        SmartTrafficSystem.VehicleEvent event = new SmartTrafficSystem.VehicleEvent(
                form.getVehicleId(), form.getSpeed(), form.getZone(), form.isEmergency(), System.currentTimeMillis()
        );

        List<SmartTrafficSystem.ViolationRecord> result = system.process(List.of(event));

        if (!result.isEmpty()) {
            SmartTrafficSystem.ViolationRecord v = result.get(0);

            TrafficViolation db = new TrafficViolation();
            db.setVehicleId(v.vehicleId);
            db.setSpeed(v.speed);
            db.setZone(v.zone);
            db.setFine(v.fine);

            repo.save(db);
            model.addAttribute("result", "Violation Saved Successfully");
        } else {
            model.addAttribute("result", "No violation detected");
        }

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