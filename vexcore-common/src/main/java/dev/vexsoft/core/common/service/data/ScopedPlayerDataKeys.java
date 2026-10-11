package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.Map;
import java.util.UUID;

/** Interns domain-partitioned primitive keys while preserving each primitive's legacy account key. */
public final class ScopedPlayerDataKeys<T> {

    private final DataContainerKey<T> account;
    private final Supplier<? extends T> defaults;
    private final Map<ProfileDomain, DataContainerKey<T>> profiles = new ConcurrentHashMap<>();

    public ScopedPlayerDataKeys(final DataContainerKey<T> account, final Supplier<? extends T> defaults) {
        this.account = account;
        this.defaults = defaults;
    }

    /** Returns one stable identity per domain without allocating in steady-state player access. */
    public DataContainerKey<T> key(final PlayerDataScope scope) {
        ProfileDomain domain = scope.domain().orElse(null);
        if (domain == null) {
            return account;
        }
        return profiles.computeIfAbsent(domain, ignored -> DataContainerKey.of(
            account.getName() + "_" + UUID.nameUUIDFromBytes(domain.value().getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", ""), account.getType(), defaults, PlayerDataScope.profile(domain)));
    }
}
