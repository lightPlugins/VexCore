package dev.vexsoft.core.common.service.currency;

import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;

final class RegisteredCurrency implements Currency {

    @Getter(AccessLevel.PACKAGE)
    private final String owner;
    @Getter(onMethod_ = @Override)
    private volatile CurrencyDefinition definition;
    @Getter(onMethod_ = @Override)
    private volatile boolean registered = true;

    RegisteredCurrency(final String owner, final CurrencyDefinition definition) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    @Override
    public CurrencyKey getKey() {
        return definition.getKey();
    }

    void update(final CurrencyDefinition updatedDefinition) {
        if (!definition.getDataScope().domain().equals(updatedDefinition.getDataScope().domain())
            || definition.isSharedProfile() != updatedDefinition.isSharedProfile()) {
            throw new IllegalArgumentException("Changing a registered currency scope requires an offline migration");
        }
        definition = Objects.requireNonNull(updatedDefinition, "updatedDefinition");
    }

    void unregister() {
        registered = false;
    }
}
