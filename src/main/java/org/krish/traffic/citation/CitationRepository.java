package org.krish.traffic.citation;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CitationRepository extends JpaRepository<Citation, Long> {

  List<Citation> findAllByOrderByIdDesc(Pageable pageable);

  List<Citation> findByIdLessThanOrderByIdDesc(Long id, Pageable pageable);

  List<Citation> findByZoneOrderByIdDesc(String zone, Pageable pageable);

  List<Citation> findByZoneAndIdLessThanOrderByIdDesc(String zone, Long id, Pageable pageable);

  @Query(
      "SELECT c.zone AS zone, COUNT(c) AS count, COALESCE(SUM(c.fineAmount), 0) AS fineTotal"
          + " FROM Citation c GROUP BY c.zone")
  List<ZoneCount> findZoneCounts();
}
