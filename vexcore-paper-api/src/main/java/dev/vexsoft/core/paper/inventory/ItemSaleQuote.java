package dev.vexsoft.core.paper.inventory;

import dev.vexsoft.core.number.WholeAmount;
import java.util.Objects;

/** Immutable amount and item count shown before a sale is confirmed. */
public record ItemSaleQuote(int count, WholeAmount amount) {
    /** Empty selection. */
    public static final ItemSaleQuote EMPTY = new ItemSaleQuote(0, WholeAmount.ZERO);

    /** Rejects negative counts and missing amounts. */
    public ItemSaleQuote {
        if (count < 0) {
            throw new IllegalArgumentException("Sale count must not be negative");
        }
        Objects.requireNonNull(amount, "amount");
    }
}
