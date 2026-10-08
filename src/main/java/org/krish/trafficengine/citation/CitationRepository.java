package org.krish.trafficengine.citation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CitationRepository extends JpaRepository<Citation, Long> {

  // a null zone means all zones
  @Query(
      "SELECT c FROM Citation c WHERE :zone IS NULL OR c.zone = :zone"
          + " ORDER BY c.id DESC LIMIT 50")
  List<Citation> findNewest50(@Param("zone") String zone);

  @Query(
      "SELECT c.zone AS zone, COUNT(c) AS count, COALESCE(SUM(c.fineAmount), 0) AS fineTotal"
          + " FROM Citation c GROUP BY c.zone ORDER BY COUNT(c) DESC, c.zone")
  List<ZoneSummary> findZoneSummaries();
}
