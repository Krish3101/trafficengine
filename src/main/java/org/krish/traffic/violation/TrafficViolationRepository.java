package org.krish.traffic.violation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrafficViolationRepository extends JpaRepository<TrafficViolation, Long> {

  @Query("SELECT COALESCE(SUM(v.fine), 0) FROM TrafficViolation v")
  Long sumAllFines();

  @Query("SELECT v.zone, COUNT(v) FROM TrafficViolation v GROUP BY v.zone")
  List<Object[]> countViolationsByZone();
}
