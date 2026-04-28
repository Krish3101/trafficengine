package org.krish.traffic;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrafficViolationRepository extends JpaRepository<TrafficViolation, Long> {
}