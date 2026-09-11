package org.krish.traffic.violation;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ViolationRepository extends JpaRepository<Violation, Long> {

  List<Violation> findAllByOrderByRecordedAtDescIdDesc(Pageable pageable);

  List<Violation> findByZoneIgnoreCaseOrderByRecordedAtDescIdDesc(String zone, Pageable pageable);

  @Query(
      "SELECT v.zone AS zone, COUNT(v) AS count, COALESCE(SUM(v.fineAmount), 0) AS fineTotal"
          + " FROM Violation v GROUP BY v.zone")
  List<ZoneCount> findZoneCounts();
}
