package dev.vexsoft.core.common.service.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.number.WholeAmount;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies personal wallet/receipt separation while legacy account currencies remain available. */
class VexCurrencyProfileIsolationTest {

    @Test
    void balancesAndIdempotencyReceiptsBelongToTheSelectedProfile() {
        var domain = new ProfileDomain("test:game");
        var player = new VexPlayer(UUID.randomUUID(), "Alex");
        var scoped = new RegisteredCurrency("test", CurrencyDefinition.builder(CurrencyKey.of("test", "coins"))
            .dataScope(PlayerDataScope.profile(domain)).build());
        var account = new RegisteredCurrency("test", CurrencyDefinition.builder(CurrencyKey.of("test", "global"))
            .build());
        var key = CurrencyPlayerData.key(scoped);
        var first = key.createDefaultValue();
        var second = key.createDefaultValue();
        player.install(CurrencyPlayerData.CURRENCIES, new CurrencyData());
        player.installProfile(context(player, domain), Map.of(key, first));
        var wallet = new VexCurrencyContainer(player);
        wallet.deposit(account, WholeAmount.of(42));
        wallet.depositOnce(scoped, WholeAmount.of(10), "reward");
        player.installProfile(context(player, domain), Map.of(key, second));
        assertEquals(WholeAmount.ZERO, wallet.getBalance(scoped));
        wallet.depositOnce(scoped, WholeAmount.of(10), "reward");
        assertEquals(WholeAmount.of(10), wallet.getBalance(scoped));
        player.installProfile(context(player, domain), Map.of(key, first));
        wallet.depositOnce(scoped, WholeAmount.of(10), "reward");
        assertEquals(WholeAmount.of(10), wallet.getBalance(scoped));
        assertEquals(WholeAmount.of(42), wallet.getBalance(account));
    }

    private static PlayerProfileContext context(final VexPlayer player, final ProfileDomain domain) {
        return new PlayerProfileContext(domain, UUID.randomUUID(), player.getUniqueId(), UUID.randomUUID(), 1, 1, 1);
    }
}
