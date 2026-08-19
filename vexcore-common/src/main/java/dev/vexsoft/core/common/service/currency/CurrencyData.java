package dev.vexsoft.core.common.service.currency;

import java.util.LinkedHashMap;
import java.util.Map;

final class CurrencyData {

  private Map<String, Long> balances = new LinkedHashMap<>();

  public Map<String, Long> getBalances() {
    return balances;
  }

  public void setBalances(final Map<String, Long> balances) {
    this.balances = new LinkedHashMap<>(balances);
  }
}
