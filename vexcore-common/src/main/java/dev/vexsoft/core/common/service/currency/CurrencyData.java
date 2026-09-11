package dev.vexsoft.core.common.service.currency;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class CurrencyData {

    private Map<String, String> balances = new LinkedHashMap<>();

    public Map<String, String> getBalances() {
        return balances;
    }

    public void setBalances(final Map<String, ?> balances) {
        this.balances = new LinkedHashMap<>();

        if (balances != null) {
            balances.forEach((key, value) -> this.balances.put(key, Objects.toString(value)));
        }
    }
}
