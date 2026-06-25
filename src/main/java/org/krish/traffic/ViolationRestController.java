package org.krish.traffic;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/violations")
public class ViolationRestController {

    private final TrafficViolationRepository repository;
    private final SmartTrafficSystem system;

    public ViolationRestController(TrafficViolationRepository repository, SmartTrafficSystem system) {
        this.repository = repository;
        this.system = system;
    }

    @GetMapping
    public List<TrafficViolation> getAllViolations() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> submitEvent(@Valid @RequestBody ViolationForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            List<String> errors = bindingResult.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.toList());
            return ResponseEntity.badRequest().body(Map.of("errors", errors));
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

            TrafficViolation saved = repository.save(db);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "violationDetected", true,
                    "message", "Violation saved successfully",
                    "violation", saved
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "violationDetected", false,
                    "message", "No violation detected for this event"
            ));
        }
    }
}
