package dev.vexsoft.core.common.service.currency;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;

final class CurrencyData {

    @Getter
    private Map<String, String> balances = new LinkedHashMap<>();
    @Getter
    private Map<String, String> receipts = new LinkedHashMap<>();

    public void setReceipts(final Map<String, String> values) {
        receipts = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    public void setBalances(final Map<String, ?> balances) {
        this.balances = new LinkedHashMap<>();

        if (balances != null) {
            balances.forEach((key, value) -> this.balances.put(key, Objects.toString(value)));
        }
    }
}
