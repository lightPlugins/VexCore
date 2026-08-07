package dev.vexsoft.core.gameplay.stat;

import java.util.LinkedHashMap;
import java.util.Map;

final class StatData {

  private Map<String, Double> permanentValues = new LinkedHashMap<>();

  public Map<String, Double> getPermanentValues() {
    return permanentValues;
  }

  public void setPermanentValues(final Map<String, Double> permanentValues) {
    this.permanentValues = new LinkedHashMap<>(permanentValues);
  }
}
