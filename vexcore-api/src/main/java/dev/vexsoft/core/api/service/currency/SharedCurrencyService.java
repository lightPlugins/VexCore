package dev.vexsoft.core.api.service.currency;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyTransaction;
import dev.vexsoft.core.number.WholeAmount;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Stages shared wallets inside the existing revision-checked profile transaction. */
public interface SharedCurrencyService extends VexService {

    /** Loads an authoritative wallet before the entity-thread mutation begins. */
    CompletableFuture<Void> prepare(VexPlayer player, PlayerProfileContext context);

    /** Returns a staged balance, or the latest successfully loaded display snapshot. */
    WholeAmount balance(VexPlayer player, Currency currency);

    /** Replaces a staged balance; standalone writes fail closed outside a durable profile transaction. */
    CurrencyTransaction set(VexPlayer player, Currency currency, WholeAmount balance);

    /** Returns the shared write to commit atomically with the player's inventory and other documents. */
    List<ProfileDocumentChange> changes(VexPlayer player);

    /** Retires staging and publishes only committed balances to the display cache. */
    void finish(VexPlayer player, boolean committed);

    /** Refreshes display balances without opening an economic mutation. */
    CompletableFuture<Void> refresh(VexPlayer player, PlayerProfileContext context);
}
