package org.krish.traffic;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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
        populateModel(model);
        return "index";
    }

    @PostMapping("/process")
    public String process(
            @RequestParam String vehicleId,
            @RequestParam double speed,
            @RequestParam String zone,
            @RequestParam(defaultValue = "false") boolean emergency,
            Model model) {

        SmartTrafficSystem.VehicleEvent event = new SmartTrafficSystem.VehicleEvent(
                vehicleId, speed, zone, emergency, System.currentTimeMillis()
        );

        List<SmartTrafficSystem.ViolationRecord> result = system.process(List.of(event));

        if (!result.isEmpty()) {
            SmartTrafficSystem.ViolationRecord v = result.get(0);

            TrafficViolation db = new TrafficViolation();
            db.vehicleId = v.vehicleId;
            db.speed = v.speed;
            db.zone = v.zone;
            db.fine = v.fine;

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