package org.krish.traffic.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "traffic.rules")
public class TrafficRulesProperties {

  private double speedThreshold;
  private List<FineTier> fineTiers;

  public double getSpeedThreshold() {
    return speedThreshold;
  }

  public void setSpeedThreshold(double speedThreshold) {
    this.speedThreshold = speedThreshold;
  }

  public List<FineTier> getFineTiers() {
    return fineTiers;
  }

  public void setFineTiers(List<FineTier> fineTiers) {
    this.fineTiers = fineTiers;
  }

  public static class FineTier {
    private double threshold;
    private int amount;

    public double getThreshold() {
      return threshold;
    }

    public void setThreshold(double threshold) {
      this.threshold = threshold;
    }

    public int getAmount() {
      return amount;
    }

    public void setAmount(int amount) {
      this.amount = amount;
    }
  }
}
