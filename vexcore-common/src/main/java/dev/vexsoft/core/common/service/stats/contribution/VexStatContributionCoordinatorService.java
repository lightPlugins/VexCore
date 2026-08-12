package dev.vexsoft.core.common.service.stats.contribution;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.stats.StatRegistryCoordinatorService;
import dev.vexsoft.core.stats.PlayerStat;
import dev.vexsoft.core.stats.Stat;
import dev.vexsoft.core.stats.StatContainer;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import dev.vexsoft.core.stats.StatModifierHandle;
import dev.vexsoft.core.stats.StatUpdateBatch;
import dev.vexsoft.core.stats.contribution.StatContributionProvider;
import dev.vexsoft.core.stats.contribution.StatContributionRefreshResult;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Default snapshot-based runtime stat contribution coordinator. */
@Dependencies({PlayerDataCoordinatorService.class, StatRegistryCoordinatorService.class})
public final class VexStatContributionCoordinatorService
    implements StatContributionCoordinatorService, AutoCloseable {

  private final PlayerDataCoordinatorService players;
  private final StatRegistryCoordinatorService stats;
  private final Map<ServiceOwner, Map<String, RegisteredProvider>> providers =
      new IdentityHashMap<>();
  private final Map<UUID, Map<Source, AppliedSnapshot>> snapshots = new LinkedHashMap<>();

  /** Resolves player and stat registries used for refresh operations. */
  public VexStatContributionCoordinatorService(final VexServiceRegistry services) {
    VexServiceRegistry checked = Objects.requireNonNull(services, "services");
    players = checked.require(PlayerDataCoordinatorService.class);
    stats = checked.require(StatRegistryCoordinatorService.class);
  }

  @Override
  public synchronized void register(
      final ServiceOwner owner,
      final VexServiceRegistry services,
      final String key,
      final Class<? extends StatContributionProvider> type
  ) {
    String checkedKey = validateKey(key);
    Map<String, RegisteredProvider> owned = providers.computeIfAbsent(
        Objects.requireNonNull(owner, "owner"),
        ignored -> new LinkedHashMap<>()
    );
    if (owned.containsKey(checkedKey)) {
      throw new IllegalStateException("Duplicate stat contribution provider: " + source(owner, key));
    }
    StatContributionProvider provider = VexClassFactory.create(
        Objects.requireNonNull(type, "type"),
        services,
        "STAT CONTRIBUTION PROVIDER"
    );
    owned.put(checkedKey, new RegisteredProvider(new Source(owner, checkedKey), provider));
  }

  @Override
  public synchronized boolean unregister(final ServiceOwner owner, final String key) {
    Map<String, RegisteredProvider> owned = providers.get(owner);
    RegisteredProvider removed = owned == null ? null : owned.remove(validateKey(key));
    if (removed == null) {
      return false;
    }
    if (owned.isEmpty()) {
      providers.remove(owner);
    }
    removeSource(removed.source());
    return true;
  }

  @Override
  public synchronized void unregisterOwner(final ServiceOwner owner) {
    Map<String, RegisteredProvider> removed = providers.remove(owner);
    if (removed != null) {
      removed.values().forEach(provider -> removeSource(provider.source()));
    }
  }

  @Override
  public synchronized StatContributionRefreshResult refresh(
      final VexPlayer player,
      final ServiceOwner owner,
      final String key
  ) {
    return refreshProvider(player, requireProvider(owner, key));
  }

  @Override
  public synchronized List<StatContributionRefreshResult> refresh(
      final VexPlayer player,
      final ServiceOwner owner
  ) {
    Map<String, RegisteredProvider> owned = providers.get(owner);
    return owned == null ? List.of() : refreshProviders(player, owned.values());
  }

  @Override
  public synchronized List<StatContributionRefreshResult> refreshAll(
      final ServiceOwner owner,
      final String key
  ) {
    RegisteredProvider provider = requireProvider(owner, key);
    return players.getLoadedPlayers().stream()
        .map(player -> refreshProvider(player, provider))
        .toList();
  }

  @Override
  public synchronized List<StatContributionRefreshResult> refreshAll(final ServiceOwner owner) {
    Map<String, RegisteredProvider> owned = providers.get(owner);
    if (owned == null) {
      return List.of();
    }
    List<StatContributionRefreshResult> results = new ArrayList<>();
    for (VexPlayer player : players.getLoadedPlayers()) {
      results.addAll(refreshProviders(player, owned.values()));
    }
    return List.copyOf(results);
  }

  @Override
  public synchronized List<StatContributionRefreshResult> refreshPlayer(final VexPlayer player) {
    List<RegisteredProvider> all = providers.values().stream()
        .flatMap(owned -> owned.values().stream())
        .toList();
    return refreshProviders(player, all);
  }

  @Override
  public synchronized void removePlayer(final VexPlayer player) {
    Map<Source, AppliedSnapshot> removed = snapshots.remove(player.getUniqueId());
    if (removed != null) {
      removed.values().forEach(AppliedSnapshot::remove);
    }
  }

  @Override
  public synchronized void close() {
    snapshots.values().forEach(values -> values.values().forEach(AppliedSnapshot::remove));
    snapshots.clear();
    providers.clear();
  }

  private List<StatContributionRefreshResult> refreshProviders(
      final VexPlayer player,
      final Iterable<RegisteredProvider> selected
  ) {
    List<StatContributionRefreshResult> results = new ArrayList<>();
    for (RegisteredProvider provider : selected) {
      results.add(refreshProvider(player, provider));
    }
    return List.copyOf(results);
  }

  private StatContributionRefreshResult refreshProvider(
      final VexPlayer player,
      final RegisteredProvider registered
  ) {
    String source = registered.source().toString();
    try {
      Map<StatKey, StatModifier> calculated = Map.copyOf(
          Objects.requireNonNull(registered.provider().calculate(player), "provider result")
      );
      Map<StatKey, ResolvedModifier> resolved = new LinkedHashMap<>();
      for (Map.Entry<StatKey, StatModifier> entry : calculated.entrySet()) {
        StatKey key = Objects.requireNonNull(entry.getKey(), "stat key");
        Stat stat = stats.find(key).orElseThrow(
            () -> new IllegalStateException("Stat is not registered: " + key)
        );
        resolved.put(key, new ResolvedModifier(stat, Objects.requireNonNull(entry.getValue())));
      }
      replace(player, registered.source(), resolved);
      return new StatContributionRefreshResult(source, calculated.keySet(), true, "");
    } catch (RuntimeException exception) {
      return new StatContributionRefreshResult(source, Set.of(), false, exception.getMessage());
    }
  }

  private void replace(
      final VexPlayer player,
      final Source source,
      final Map<StatKey, ResolvedModifier> resolved
  ) {
    StatContainer container = player.getContainer(StatContainer.class);
    Map<Source, AppliedSnapshot> playerSnapshots = snapshots.computeIfAbsent(
        player.getUniqueId(),
        ignored -> new LinkedHashMap<>()
    );
    AppliedSnapshot previous = playerSnapshots.remove(source);
    List<StatModifierHandle> handles = new ArrayList<>();
    try (StatUpdateBatch ignored = container.beginUpdate()) {
      if (previous != null) {
        previous.remove();
      }
      for (ResolvedModifier value : resolved.values()) {
        PlayerStat stat = container.getStat(value.stat());
        handles.add(stat.addModifier(value.modifier()));
      }
    }
    if (!handles.isEmpty()) {
      playerSnapshots.put(source, new AppliedSnapshot(List.copyOf(handles)));
    }
    if (playerSnapshots.isEmpty()) {
      snapshots.remove(player.getUniqueId());
    }
  }

  private RegisteredProvider requireProvider(final ServiceOwner owner, final String key) {
    Map<String, RegisteredProvider> owned = providers.get(owner);
    RegisteredProvider provider = owned == null ? null : owned.get(validateKey(key));
    if (provider == null) {
      throw new IllegalStateException("Stat contribution provider is not registered: " + source(owner, key));
    }
    return provider;
  }

  private void removeSource(final Source source) {
    for (Map<Source, AppliedSnapshot> playerSnapshots : snapshots.values()) {
      AppliedSnapshot removed = playerSnapshots.remove(source);
      if (removed != null) {
        removed.remove();
      }
    }
    snapshots.entrySet().removeIf(entry -> entry.getValue().isEmpty());
  }

  private static String validateKey(final String key) {
    String checked = Objects.requireNonNull(key, "key");
    if (!checked.matches("[a-z][a-z0-9_-]*")) {
      throw new IllegalArgumentException("Provider key must be a lowercase identifier: " + checked);
    }
    return checked;
  }

  private static String source(final ServiceOwner owner, final String key) {
    return owner.getServiceOwnerName() + ':' + key;
  }

  private record Source(ServiceOwner owner, String key) {
    @Override
    public String toString() {
      return source(owner, key);
    }
  }

  private record RegisteredProvider(Source source, StatContributionProvider provider) {}

  private record ResolvedModifier(Stat stat, StatModifier modifier) {}

  private record AppliedSnapshot(List<StatModifierHandle> handles) {
    private void remove() {
      handles.forEach(StatModifierHandle::remove);
    }
  }
}
