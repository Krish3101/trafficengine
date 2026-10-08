package org.krish.trafficengine.citation;

import java.math.BigDecimal;

public interface ZoneSummary {

  String getZone();

  long getCount();

  BigDecimal getFineTotal();
}
