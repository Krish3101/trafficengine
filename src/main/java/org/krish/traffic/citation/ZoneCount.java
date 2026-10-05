package org.krish.traffic.citation;

import java.math.BigDecimal;

public interface ZoneCount {

  String getZone();

  long getCount();

  BigDecimal getFineTotal();
}
