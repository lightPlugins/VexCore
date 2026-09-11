package dev.vexsoft.core.currency;

import dev.vexsoft.core.number.WholeAmount;
import java.util.Objects;
import java.util.Optional;

/** Immutable balance and localization rules for one virtual currency. */
public final class CurrencyDefinition {

    private final CurrencyKey key;
    private final WholeAmount defaultBalance;
    private final WholeAmount maximumBalance;
    private final String nameKey;
    private final String formatKey;

    private CurrencyDefinition(final Builder builder) {
        key = builder.key;
        defaultBalance = Objects.requireNonNull(builder.defaultBalance, "defaultBalance");
        maximumBalance = builder.maximumBalance;

        if (maximumBalance != null && defaultBalance.compareTo(maximumBalance) > 0) {
            throw new IllegalArgumentException("Currency default balance must not exceed its maximum");
        }

        nameKey = requireLocalizationKey(builder.nameKey, "nameKey");
        formatKey = requireLocalizationKey(builder.formatKey, "formatKey");
    }

    /** Starts building a definition for the supplied stable key. */
    public static Builder builder(final CurrencyKey key) {
        return new Builder(key);
    }

    public CurrencyKey getKey() {
        return key;
    }

    public WholeAmount getDefaultBalance() {
        return defaultBalance;
    }

    public Optional<WholeAmount> getMaximumBalance() {
        return Optional.ofNullable(maximumBalance);
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getFormatKey() {
        return formatKey;
    }

    private static String requireLocalizationKey(final String value, final String name) {
        String checked = Objects.requireNonNull(value, name).trim();

        if (checked.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }

        return checked;
    }

    /** Mutable builder for a virtual-currency definition. */
    public static final class Builder {

        private final CurrencyKey key;
        private WholeAmount defaultBalance = WholeAmount.ZERO;
        private WholeAmount maximumBalance;
        private String nameKey;
        private String formatKey;

        private Builder(final CurrencyKey key) {
            this.key = Objects.requireNonNull(key, "key");
            nameKey = "currencies." + key.value() + ".name";
            formatKey = "currencies." + key.value() + ".format";
        }

        /** Sets the balance used when a player has no persisted entry yet. */
        public Builder defaultBalance(final WholeAmount value) {
            defaultBalance = Objects.requireNonNull(value, "value");

            return this;
        }

        /** Sets the inclusive maximum persistent balance. */
        public Builder maximumBalance(final WholeAmount value) {
            maximumBalance = Objects.requireNonNull(value, "value");

            return this;
        }

        /** Overrides the default {@code currencies.<currency>.name} localization key. */
        public Builder nameKey(final String value) {
            nameKey = value;

            return this;
        }

        /**
         * Overrides the default {@code currencies.<currency>.format} localization key.
         * The localized value may use {@code %amount%} and {@code %formatted_amount%}.
         */
        public Builder formatKey(final String value) {
            formatKey = value;

            return this;
        }

        /** Creates the validated immutable definition. */
        public CurrencyDefinition build() {
            return new CurrencyDefinition(this);
        }
    }
}
