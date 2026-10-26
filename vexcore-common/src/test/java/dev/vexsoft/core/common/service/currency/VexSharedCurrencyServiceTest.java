package dev.vexsoft.core.common.service.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.currency.SharedCurrencyService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Uses production registry construction and two independent backend caches over one atomic store. */
class VexSharedCurrencyServiceTest {

    private static final ProfileDomain DOMAIN = new ProfileDomain("test:game");

    @Test
    void separateBackendsCannotOverspendOneCooperativeWallet() throws Exception {
        var store = new Storage();
        var first = new Backend(store);
        var second = new Backend(store);
        UUID profile = UUID.randomUUID();
        var alice = player(profile);
        var bob = player(profile);
        var aliceWallet = new VexCurrencyContainer(alice, first.shared);
        var bobWallet = new VexCurrencyContainer(bob, second.shared);
        assertFalse(aliceWallet.deposit(first.currency, WholeAmount.of(100)).successful());
        first.prepare(alice);
        assertTrue(aliceWallet.deposit(first.currency, WholeAmount.of(100)).successful());
        assertTrue(store.commit(first.shared.changes(alice)).join());
        first.shared.finish(alice, true);
        first.prepare(alice);
        second.prepare(bob);
        assertTrue(aliceWallet.withdraw(first.currency, WholeAmount.of(80)).successful());
        assertTrue(bobWallet.withdraw(second.currency, WholeAmount.of(80)).successful());
        assertTrue(store.commit(first.shared.changes(alice)).join());
        assertFalse(store.commit(second.shared.changes(bob)).join());
        first.shared.finish(alice, true);
        second.shared.finish(bob, false);
        second.shared.refresh(bob, bob.findProfileContext(DOMAIN).orElseThrow()).join();
        assertEquals(WholeAmount.of(20), bobWallet.getBalance(second.currency));
    }

    @Test
    void failedAtomicRewardsRestoreSharedBalancesBeforeTheDurableCommit() throws Exception {
        var backend = new Backend(new Storage());
        var player = player(UUID.randomUUID());
        var wallet = new VexCurrencyContainer(player, backend.shared);
        backend.prepare(player);
        assertFalse(player.atomic(value -> value, () -> {
            assertTrue(wallet.deposit(backend.currency, WholeAmount.of(50)).successful());
            return false;
        }));
        assertEquals(WholeAmount.ZERO, wallet.getBalance(backend.currency));
        backend.shared.finish(player, false);
    }

    @Test
    void hugeSharedBalancesSurviveReloadAndRemainIsolatedBetweenProfiles() throws Exception {
        var store = new Storage();
        var backend = new Backend(store);
        UUID profile = UUID.randomUUID();
        var player = player(profile);
        var wallet = new VexCurrencyContainer(player, backend.shared);
        var huge = WholeAmountFormatter.parse("99zz");
        backend.prepare(player);
        assertTrue(wallet.deposit(backend.currency, huge).successful());
        assertTrue(store.commit(backend.shared.changes(player)).join());
        backend.shared.finish(player, true);
        var restarted = new Backend(store);
        var otherMember = player(profile);
        restarted.shared.refresh(otherMember, otherMember.findProfileContext(DOMAIN).orElseThrow()).join();
        assertEquals(huge, new VexCurrencyContainer(otherMember, restarted.shared).getBalance(restarted.currency));
        player.installProfile(context(player, UUID.randomUUID()), Map.of());
        assertEquals(WholeAmount.ZERO, wallet.getBalance(backend.currency));
    }

    @Test
    void aMixedBatchRollsPersonalCurrencyBackWhenSharedWritesAreUnavailable() throws Exception {
        var backend = new Backend(new Storage());
        var player = player(UUID.randomUUID());
        player.install(CurrencyPlayerData.CURRENCIES, new CurrencyData());
        var coins = new RegisteredCurrency("test", CurrencyDefinition.builder(CurrencyKey.of("test", "coins")).build());
        var wallet = new VexCurrencyContainer(player, backend.shared);
        assertTrue(wallet.deposit(coins, WholeAmount.of(10)).successful());
        var batch = new LinkedHashMap<Currency, WholeAmount>();
        batch.put(coins, WholeAmount.of(5));
        batch.put(backend.currency, WholeAmount.of(5));
        assertFalse(wallet.depositAll(batch).successful());
        assertEquals(WholeAmount.of(10), wallet.getBalance(coins));
        assertEquals(WholeAmount.ZERO, wallet.getBalance(backend.currency));
    }

    private static VexPlayer player(final UUID profile) {
        var player = new VexPlayer(UUID.randomUUID(), "Member");
        player.installProfile(context(player, profile), Map.of());
        return player;
    }

    private static PlayerProfileContext context(final VexPlayer player, final UUID profile) {
        return new PlayerProfileContext(DOMAIN, profile, player.getUniqueId(), UUID.randomUUID(), 1, 1, 1);
    }

    private static final class Backend {

        private final SharedCurrencyService shared;
        private final Currency currency;

        private Backend(final Storage storage) throws Exception {
            var registry = new DefaultServiceRegistry();
            ServiceOwner owner = () -> "test";
            var inject = DefaultServiceRegistry.class.getDeclaredMethod("register", ServiceOwner.class, Class.class, VexService.class);
            inject.setAccessible(true);
            inject.invoke(registry, owner, ProfileStorageService.class, storage);
            var scope = registry.scoped(owner);
            scope.register(CurrencyRegistryCoordinatorService.class, VexCurrencyRegistryCoordinatorService.class);
            scope.register(CurrencyRegistry.class, VexCurrencyRegistry.class);
            scope.register(SharedCurrencyService.class, VexSharedCurrencyService.class);
            scope.registerQueuedServices();
            shared = scope.require(SharedCurrencyService.class);
            currency = scope.require(CurrencyRegistry.class).register(CurrencyDefinition.builder(CurrencyKey.of("test", "luma"))
                .dataScope(PlayerDataScope.profile(DOMAIN)).sharedProfile(true).build());
        }

        private void prepare(final VexPlayer player) {
            shared.prepare(player, player.findProfileContext(DOMAIN).orElseThrow()).join();
        }
    }

    private static final class Storage implements ProfileStorageService {

        private final Map<ProfileDocumentKey, StoredProfileDocument> documents = new LinkedHashMap<>();

        @Override
        public synchronized CompletableFuture<Map<ProfileDocumentKey, StoredProfileDocument>> load(final Collection<ProfileDocumentKey> keys) {
            var result = new LinkedHashMap<ProfileDocumentKey, StoredProfileDocument>();
            keys.forEach(key -> {
                if (documents.containsKey(key)) {
                    result.put(key, documents.get(key));
                }
            });
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public synchronized CompletableFuture<Boolean> commit(final Collection<ProfileDocumentChange> changes) {
            for (var change : changes) {
                var before = documents.get(change.key());
                if ((before == null ? 0 : before.revision()) != change.expectedRevision()) {
                    return CompletableFuture.completedFuture(false);
                }
            }
            changes.forEach(change -> {
                if (change.value() != null) {
                    documents.put(change.key(), new StoredProfileDocument(change.value(), change.expectedRevision() + 1));
                }
            });
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Long> databaseTime() {
            return CompletableFuture.completedFuture(1000L);
        }
    }
}
