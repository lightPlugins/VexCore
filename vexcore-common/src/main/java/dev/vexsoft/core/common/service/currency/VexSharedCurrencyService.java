package dev.vexsoft.core.common.service.currency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.service.currency.SharedCurrencyService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyTransaction;
import dev.vexsoft.core.number.WholeAmount;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Uses shared profile documents and CAS writes without putting shared balances in personal snapshots. */
@Dependencies({ProfileStorageService.class, CurrencyRegistryCoordinatorService.class})
public final class VexSharedCurrencyService implements SharedCurrencyService {

    private final ProfileStorageService storage;
    private final CurrencyRegistryCoordinatorService currencies;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<VexPlayer, Wallet> working = new ConcurrentHashMap<>();
    private final java.util.Set<ProfileDocumentKey> refreshing = ConcurrentHashMap.newKeySet();
    private final Cache<ProfileDocumentKey, Wallet> displays = Caffeine.newBuilder()
        .maximumSize(10000).expireAfterAccess(Duration.ofMinutes(10)).build();

    public VexSharedCurrencyService(final VexServiceRegistry services) {
        storage = services.require(ProfileStorageService.class);
        currencies = services.require(CurrencyRegistryCoordinatorService.class);
    }

    @Override
    public CompletableFuture<Void> prepare(final VexPlayer player, final PlayerProfileContext context) {
        if (!participates(context)) {
            return CompletableFuture.completedFuture(null);
        }
        return load(context).thenAccept(wallet -> {
            if (!player.isCurrentContext(context) || working.putIfAbsent(player, wallet) != null) {
                throw new IllegalStateException("Shared wallet session changed or is already busy");
            }
        });
    }

    @Override
    public WholeAmount balance(final VexPlayer player, final Currency currency) {
        var context = player.findProfileContext(currency.getDefinition().getDataScope().domain().orElseThrow())
            .orElseThrow(() -> new IllegalStateException("Shared wallet requires an active profile"));
        var wallet = working.get(player);
        if (wallet == null || !wallet.context.equals(context)) {
            wallet = displays.getIfPresent(key(context));
            if ((wallet == null || System.nanoTime() - wallet.loadedAt > 2_000_000_000L)
                && refreshing.add(key(context))) {
                refresh(player, context).whenComplete((ignored, failure) -> refreshing.remove(key(context)));
            }
        }
        return wallet == null ? currency.getDefinition().getDefaultBalance() : amount(wallet, currency);
    }

    @Override
    public CurrencyTransaction set(final VexPlayer player, final Currency currency, final WholeAmount balance) {
        var wallet = working.get(player);
        WholeAmount previous = balance(player, currency);
        if (wallet == null || !player.isCurrentContext(wallet.context)
            || !currency.getDefinition().getDataScope().domain().filter(wallet.context.domain()::equals).isPresent()) {
            return new CurrencyTransaction(CurrencyTransaction.Status.UNAVAILABLE, previous, previous,
                "shared-currency-requires-durable-transaction");
        }
        if (currency.getDefinition().getMaximumBalance().map(max -> balance.compareTo(max) > 0).orElse(false)) {
            return new CurrencyTransaction(CurrencyTransaction.Status.MAXIMUM_EXCEEDED, previous, previous,
                "maximum-exceeded");
        }
        String id = currency.getKey().toString();
        String old = wallet.balances.put(id, balance.toString());
        wallet.dirty = true;
        player.afterRollback(() -> {
            if (old == null) {
                wallet.balances.remove(id);
            } else {
                wallet.balances.put(id, old);
            }
        });
        return new CurrencyTransaction(CurrencyTransaction.Status.SUCCESS, previous, balance, "");
    }

    @Override
    public List<ProfileDocumentChange> changes(final VexPlayer player) {
        var wallet = working.get(player);
        if (wallet == null || !wallet.dirty) {
            return List.of();
        }
        try {
            return List.of(new ProfileDocumentChange(key(wallet.context), wallet.revision,
                mapper.writeValueAsString(wallet.balances)));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot serialize shared currency account", failure);
        }
    }

    @Override
    public void finish(final VexPlayer player, final boolean committed) {
        var wallet = working.remove(player);
        if (wallet != null && committed) {
            cache(new Wallet(wallet.context, wallet.revision + (wallet.dirty ? 1 : 0), wallet.balances));
        }
    }

    @Override
    public CompletableFuture<Void> refresh(final VexPlayer player, final PlayerProfileContext context) {
        return participates(context) ? load(context).thenAccept(this::cache) : CompletableFuture.completedFuture(null);
    }

    private boolean participates(final PlayerProfileContext context) {
        return currencies.getRegisteredCurrencies().stream().anyMatch(currency -> currency.getDefinition().isSharedProfile()
            && currency.getDefinition().getDataScope().domain().filter(context.domain()::equals).isPresent());
    }

    private CompletableFuture<Wallet> load(final PlayerProfileContext context) {
        var key = key(context);
        return storage.load(List.of(key)).thenApply(documents -> {
            var document = documents.get(key);
            try {
                Map<String, String> balances = document == null ? Map.of()
                    : mapper.readValue(document.value(), new TypeReference<Map<String, String>>() { });
                balances.values().forEach(WholeAmount::parse);
                var wallet = new Wallet(context, document == null ? 0 : document.revision(), balances);
                cache(wallet);
                return wallet;
            } catch (Exception failure) {
                throw new IllegalStateException("Invalid shared currency account", failure);
            }
        });
    }

    private void cache(final Wallet wallet) {
        displays.asMap().compute(key(wallet.context), (key, previous) -> previous == null
            || previous.revision <= wallet.revision ? new Wallet(wallet.context, wallet.revision, wallet.balances) : previous);
    }

    private static WholeAmount amount(final Wallet wallet, final Currency currency) {
        String stored = wallet.balances.get(currency.getKey().toString());
        return stored == null ? currency.getDefinition().getDefaultBalance() : WholeAmount.parse(stored);
    }

    private static ProfileDocumentKey key(final PlayerProfileContext context) {
        return new ProfileDocumentKey(PlayerDataAddress.shared(context.domain(), context.profileId()),
            "vexcore", "shared_currencies");
    }

    private static final class Wallet {

        private final PlayerProfileContext context;
        private final long revision;
        private final Map<String, String> balances;
        private boolean dirty;
        private final long loadedAt = System.nanoTime();

        private Wallet(final PlayerProfileContext context, final long revision, final Map<String, String> balances) {
            this.context = context;
            this.revision = revision;
            this.balances = new LinkedHashMap<>(balances);
        }
    }
}
