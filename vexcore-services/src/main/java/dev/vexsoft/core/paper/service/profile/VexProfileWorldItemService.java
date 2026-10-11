package dev.vexsoft.core.paper.service.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.ProfileMutation;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.world.WorldKey;
import dev.vexsoft.core.cache.VexCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.paper.profile.PaperProfileState;
import dev.vexsoft.core.paper.profile.ProfileItemRealm;
import dev.vexsoft.core.paper.service.network.ServerIdentityService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.world.WorldService;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;

/** Commits native ownership and shared world journals atomically; native items never become an authority. */
@Dependencies({ProfileSessionService.class, ProfileStorageService.class, PlayerDataCoordinatorService.class,
    ScheduleService.class, WorldService.class, ServerIdentityService.class, CacheService.class})
public final class VexProfileWorldItemService implements ProfileWorldItemService, AutoCloseable {

    private static final NamespacedKey DROP_ID = new NamespacedKey("vexcore", "profile_world_drop");
    private static final NamespacedKey PUBLIC_RECEIPT = new NamespacedKey("vexcore", "profile_loot_receipt");
    private static final int MAXIMUM_DROPS = 512;
    private final ProfileSessionService sessions;
    private final ProfileStorageService storage;
    private final PlayerDataCoordinatorService players;
    private final ScheduleService schedules;
    private final WorldService worlds;
    private final String server;
    private final Function<byte[], ItemStack> decodeItem;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<VexPlayer, ProfileDocumentKey> connections = new ConcurrentHashMap<>();
    private final Map<ProfileDocumentKey, Map<UUID, WorldDrop>> indexes = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Map<UUID, WorldDrop>> chunks = new ConcurrentHashMap<>();
    private final Map<UUID, Item> projections = new ConcurrentHashMap<>();
    private final Set<UUID> collecting = ConcurrentHashMap.newKeySet();
    private final VexCache<UUID, Boolean> attempts;
    private volatile boolean closed;

    public VexProfileWorldItemService(final VexServiceRegistry services) {
        this(services, ItemStack::deserializeBytes);
    }

    VexProfileWorldItemService(final VexServiceRegistry services, final Function<byte[], ItemStack> decodeItem) {
        this.decodeItem = Objects.requireNonNull(decodeItem, "decodeItem");
        sessions = services.require(ProfileSessionService.class);
        storage = services.require(ProfileStorageService.class);
        players = services.require(PlayerDataCoordinatorService.class);
        schedules = services.require(ScheduleService.class);
        worlds = services.require(WorldService.class);
        server = services.require(ServerIdentityService.class).getServerId().value();
        attempts = services.require(CacheService.class).create("profile_world_item_attempts",
            VexCacheOptions.builder().maximumSize(16_384).expireAfterWrite(Duration.ofMillis(250)).build());
    }

    @Override
    public CompletableFuture<Void> drop(final VexPlayer player, final List<ItemStack> items, final Location location) {
        PlayerProfileContext context = sessions.activeContext(player).orElseThrow();
        var originals = items.stream().filter(item -> item != null && !item.isEmpty())
            .filter(item -> sessions.persistItem(player, item)).map(ItemStack::clone).toList();
        if (originals.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (originals.size() > PaperProfileState.MAXIMUM_NATIVE_POSSESSIONS) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Too many native drops"));
        }
        var position = new Position(worlds.getKey(Objects.requireNonNull(location.getWorld())).asString(),
            location.getX(), location.getY(), location.getZ());
        var serialized = originals.stream().map(item -> {
            if (!ProfileItemRealm.permits(item, context)) {
                throw new IllegalStateException("Dropped possession belongs to another profile");
            }
            ItemStack scoped = item.clone();
            ProfileItemRealm.bind(scoped, context);
            return Base64.getEncoder().encodeToString(scoped.serializeAsBytes());
        }).toList();
        ProfileDocumentKey key = key(context);
        return load(key).thenCompose(index -> storage.databaseTime().thenCompose(now -> {
            Map<UUID, WorldDrop> next = live(index.drops(), now);
            if (next.size() + originals.size() > MAXIMUM_DROPS) {
                throw new IllegalStateException("Profile world drop capacity exceeded");
            }
            var added = new LinkedHashMap<UUID, WorldDrop>();
            serialized.forEach(stack -> added.put(UUID.randomUUID(), new WorldDrop(server, position, stack, now + 300_000)));
            next.putAll(added);
            ProfileDocumentChange change = change(key, index.revision(), next);
            return sessions.transact(player, () -> {
                requireCurrent(player, context);
                remove(player.requirePlatformPlayer(Player.class), originals);
                player.afterCommit(() -> {
                    remember(key, next);
                    added.forEach(this::project);
                });
                return new ProfileMutation<Void>(null, List.of(change));
            });
        }));
    }

    @Override
    public CompletableFuture<Void> collect(final VexPlayer player, final Item projection) {
        UUID id = identity(projection);
        PlayerProfileContext context = sessions.activeContext(player).orElse(null);
        if (context == null || !sessions.isReady(player) || !ProfileItemRealm.permits(projection.getItemStack(), context)) {
            return CompletableFuture.completedFuture(null);
        }
        ItemStack candidate = projection.getItemStack().clone();
        if (id == null) {
            ProfileItemRealm.bind(candidate, context);
        }
        // A full inventory must not start repeated snapshots, journal reads and database writes each pickup tick.
        if (!canAccept(player.requirePlatformPlayer(Player.class).getInventory(), candidate)) {
            return CompletableFuture.completedFuture(null);
        }
        UUID attempt = id == null ? projection.getUniqueId() : id;
        if (attempts.getIfPresent(attempt).isPresent()) {
            return CompletableFuture.completedFuture(null);
        }
        attempts.put(attempt, true);
        if (id == null) {
            return collectPublic(player, projection, context);
        }
        if (!collecting.add(id)) {
            return CompletableFuture.completedFuture(null);
        }
        ProfileDocumentKey key = key(context);
        return load(key).thenCompose(index -> storage.databaseTime().thenCompose(now -> {
            WorldDrop drop = index.drops().get(id);
            if (drop == null || drop.expiresAt() <= now) {
                schedules.runFor(projection, projection::remove);
                return CompletableFuture.completedFuture(null);
            }
            return sessions.transact(player, () -> {
                requireCurrent(player, context);
                Player platform = player.requirePlatformPlayer(Player.class);
                if (!projection.isValid() || platform.getWorld() != projection.getWorld()
                    || platform.getLocation().distanceSquared(projection.getLocation()) > 16) {
                    throw new IllegalStateException("World item left pickup range");
                }
                // Grant from the journal. A changed or duplicated visual entity cannot change the payout.
                ItemStack stack = decodeItem.apply(Base64.getDecoder().decode(drop.stack()));
                if (!ProfileItemRealm.permits(stack, context)) {
                    throw new IllegalStateException("World journal belongs to another profile");
                }
                int originalAmount = stack.getAmount();
                var overflow = platform.getInventory().addItem(stack);
                var next = new LinkedHashMap<>(index.drops());
                if (overflow.isEmpty()) {
                    next.remove(id);
                } else {
                    ItemStack remaining = overflow.values().iterator().next();
                    if (remaining.getAmount() == originalAmount) {
                        return ProfileMutation.<Void>of(null);
                    }
                    next.put(id, new WorldDrop(drop.server(), drop.position(),
                        Base64.getEncoder().encodeToString(remaining.serializeAsBytes()), drop.expiresAt()));
                }
                player.afterCommit(() -> {
                    remember(key, next);
                    if (next.containsKey(id)) {
                        projection.setItemStack(decodeItem.apply(Base64.getDecoder().decode(next.get(id).stack())));
                    } else {
                        projection.remove();
                        projections.remove(id);
                    }
                });
                var changed = change(key, index.revision(), next);
                return new ProfileMutation<Void>(null, List.of(new ProfileDocumentChange(key, index.revision(),
                    changed.value(), drop.expiresAt())));
            });
        })).whenComplete((ignored, failure) -> collecting.remove(id));
    }

    @Override
    public CompletableFuture<Void> reconcile(final VexPlayer player) {
        PlayerProfileContext context = sessions.activeContext(player).orElse(null);
        if (context == null || !sessions.isReady(player)) {
            return CompletableFuture.completedFuture(null);
        }
        ProfileDocumentKey key = key(context);
        ProfileDocumentKey previous = connections.put(player, key);
        if (previous != null && !previous.equals(key)) {
            retireCache(previous);
        }
        return load(key).thenCompose(index -> storage.databaseTime().thenAccept(now -> {
            if (!player.isCurrentContext(context) || !key.equals(connections.get(player))) {
                return;
            }
            Map<UUID, WorldDrop> live = live(index.drops(), now);
            remember(key, live);
            live.forEach(this::project);
        }));
    }

    @Override
    public void chunkLoaded(final World world, final int x, final int z) {
        String worldKey;
        try {
            worldKey = worlds.getKey(world).asString();
        } catch (IllegalArgumentException unavailable) {
            return;
        }
        chunks.getOrDefault(new ChunkKey(worldKey, x, z), Map.of()).forEach(this::project);
    }

    @Override
    public CompletableFuture<Void> expire(final Item projection) {
        UUID id = identity(projection);
        String realm = ProfileItemRealm.realm(projection.getItemStack());
        if (id == null) {
            return expirePublic(projection);
        }
        if (realm == null) {
            return CompletableFuture.completedFuture(null);
        }
        int separator = realm.lastIndexOf('/');
        ProfileDocumentKey key = key(PlayerDataAddress.shared(new ProfileDomain(realm.substring(0, separator)),
            UUID.fromString(realm.substring(separator + 1))));
        return load(key).thenCompose(index -> storage.databaseTime().thenCompose(now -> {
            WorldDrop drop = index.drops().get(id);
            if (drop != null && drop.expiresAt() > now) {
                return CompletableFuture.completedFuture(null);
            }
            var next = new LinkedHashMap<>(index.drops());
            next.remove(id);
            return storage.commit(List.of(change(key, index.revision(), next))).thenAccept(committed -> {
                if (committed) {
                    remember(key, next);
                    schedules.runFor(projection, projection::remove);
                    projections.remove(id);
                }
            });
        }));
    }

    @Override
    public boolean isProjection(final Item item) {
        return item.getPersistentDataContainer().has(DROP_ID)
            || item.getPersistentDataContainer().has(PUBLIC_RECEIPT);
    }

    @Override
    public void forget(final VexPlayer player) {
        ProfileDocumentKey previous = connections.remove(player);
        if (previous != null) {
            retireCache(previous);
        }
    }

    @Override
    public void close() {
        closed = true;
        attempts.invalidateAll();
        projections.values().forEach(item -> schedules.runFor(item, item::remove));
        projections.clear();
        indexes.clear();
        chunks.clear();
        connections.clear();
    }

    private CompletableFuture<Index> load(final ProfileDocumentKey key) {
        return storage.load(List.of(key)).thenApply(documents -> {
            var stored = documents.get(key);
            try {
                Map<UUID, WorldDrop> drops = stored == null ? Map.of() : mapper.readValue(stored.value(), Journal.class).drops();
                return new Index(stored == null ? 0 : stored.revision(), drops);
            } catch (JsonProcessingException invalid) {
                throw new IllegalStateException("Invalid profile world journal", invalid);
            }
        });
    }

    private CompletableFuture<Void> collectPublic(final VexPlayer player, final Item projection,
        final PlayerProfileContext context) {
        UUID id = projection.getUniqueId();
        if (!collecting.add(id)) {
            return CompletableFuture.completedFuture(null);
        }
        // Mark the native source before IO so merges, hoppers or damage cannot mutate the captured payout.
        if (!projection.getPersistentDataContainer().has(PUBLIC_RECEIPT)) {
            // The receipt owns one five-minute lifetime; avoid a despawn event and DB read on every tick.
            projection.setTicksLived(1);
            projection.getPersistentDataContainer().set(PUBLIC_RECEIPT, PersistentDataType.BYTE, (byte) 1);
        }
        projection.setCanMobPickup(false);
        String original = Base64.getEncoder().encodeToString(projection.getItemStack().serializeAsBytes());
        var key = new ProfileDocumentKey(PlayerDataAddress.shared(new ProfileDomain("vexcore:world_loot"), id),
            "vexcore", "pickup");
        return storage.load(List.of(key)).thenCompose(documents -> storage.databaseTime().thenCompose(now -> {
            var stored = documents.get(key);
            PublicLoot source;
            try {
                source = stored == null ? new PublicLoot(original, now + 300_000)
                    : mapper.readValue(stored.value(), PublicLoot.class);
            } catch (JsonProcessingException invalid) {
                throw new IllegalStateException("Invalid public loot receipt", invalid);
            }
            if (source.stack().isEmpty() || source.expiresAt() <= now) {
                schedules.runFor(projection, projection::remove);
                return CompletableFuture.completedFuture(null);
            }
            long revision = stored == null ? 0 : stored.revision();
            return sessions.transact(player, () -> {
                requireCurrent(player, context);
                Player platform = player.requirePlatformPlayer(Player.class);
                if (!projection.isValid() || platform.getWorld() != projection.getWorld()
                    || platform.getLocation().distanceSquared(projection.getLocation()) > 16) {
                    throw new IllegalStateException("Public loot left pickup range");
                }
                ItemStack stack = decodeItem.apply(Base64.getDecoder().decode(source.stack()));
                int amount = stack.getAmount();
                ProfileItemRealm.bind(stack, context);
                var overflow = platform.getInventory().addItem(stack);
                ItemStack remaining = overflow.isEmpty() ? null : overflow.values().iterator().next();
                if (remaining != null && remaining.getAmount() == amount) {
                    return ProfileMutation.<Void>of(null);
                }
                // Remaining public loot keeps its original provenance, rather than becoming the first buyer's realm.
                ItemStack publicRemainder = decodeItem.apply(Base64.getDecoder().decode(source.stack()));
                publicRemainder.setAmount(remaining == null ? 0 : remaining.getAmount());
                String encoded = remaining == null ? ""
                    : Base64.getEncoder().encodeToString(publicRemainder.serializeAsBytes());
                String receipt;
                try {
                    receipt = mapper.writeValueAsString(new PublicLoot(encoded, source.expiresAt()));
                } catch (JsonProcessingException invalid) {
                    throw new IllegalStateException("Cannot retain public loot receipt", invalid);
                }
                player.afterCommit(() -> {
                    if (remaining == null) {
                        projection.remove();
                    } else {
                        projection.setItemStack(publicRemainder);
                    }
                });
                return new ProfileMutation<Void>(null,
                    List.of(new ProfileDocumentChange(key, revision, receipt, source.expiresAt())));
            });
        })).whenComplete((ignored, failure) -> collecting.remove(id));
    }

    private CompletableFuture<Void> expirePublic(final Item projection) {
        UUID id = projection.getUniqueId();
        var key = new ProfileDocumentKey(PlayerDataAddress.shared(new ProfileDomain("vexcore:world_loot"), id),
            "vexcore", "pickup");
        return storage.load(List.of(key)).thenCompose(documents -> storage.databaseTime().thenCompose(now -> {
            var stored = documents.get(key);
            if (stored == null) {
                if (!collecting.contains(id)) {
                    schedules.runFor(projection, projection::remove);
                }
                return CompletableFuture.completedFuture(null);
            }
            try {
                var source = mapper.readValue(stored.value(), PublicLoot.class);
                if (!source.stack().isEmpty() && source.expiresAt() > now) {
                    return CompletableFuture.completedFuture(null);
                }
                String expired = mapper.writeValueAsString(new PublicLoot("", source.expiresAt()));
                return storage.commit(List.of(new ProfileDocumentChange(key, stored.revision(), expired)))
                    .thenAccept(committed -> {
                        if (committed) {
                            schedules.runFor(projection, projection::remove);
                        }
                    });
            } catch (JsonProcessingException invalid) {
                return CompletableFuture.failedFuture(new IllegalStateException("Invalid public loot receipt", invalid));
            }
        }));
    }

    private ProfileDocumentChange change(final ProfileDocumentKey key, final long revision,
        final Map<UUID, WorldDrop> drops) {
        try {
            return new ProfileDocumentChange(key, revision, mapper.writeValueAsString(new Journal(drops)));
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Cannot encode profile world journal", invalid);
        }
    }

    private void project(final UUID id, final WorldDrop drop) {
        if (closed || !drop.server().equals(server) || drop.expiresAt() <= System.currentTimeMillis()) {
            return;
        }
        schedules.runGlobal(() -> worlds.find(WorldKey.parse(drop.position().world())).ifPresent(world -> {
            Position position = drop.position();
            // Projections follow already loaded chunks; reconnect never generates hundreds of journal chunks.
            if (!world.isChunkLoaded(chunk(position.x()), chunk(position.z()))) {
                return;
            }
            Location location = new Location(world, position.x(), position.y(), position.z());
            schedules.runAt(location, () -> {
                if (closed) {
                    return;
                }
                Item existing = projections.get(id);
                if (existing != null && existing.isValid()) {
                    return;
                }
                ItemStack stack = decodeItem.apply(Base64.getDecoder().decode(drop.stack()));
                Item item = world.dropItem(location, stack);
                item.getPersistentDataContainer().set(DROP_ID, PersistentDataType.STRING, id.toString());
                item.setPersistent(false);
                item.setCanMobPickup(false);
                item.setPickupDelay(20);
                projections.put(id, item);
            });
        }));
    }

    private synchronized void remember(final ProfileDocumentKey key, final Map<UUID, WorldDrop> drops) {
        if (closed) {
            return;
        }
        Map<UUID, WorldDrop> previous = indexes.put(key, Map.copyOf(drops));
        if (previous != null) {
            previous.forEach((id, drop) -> {
                removeChunk(id, drop);
                if (!drops.containsKey(id)) {
                    removeProjection(id);
                }
            });
        }
        drops.forEach((id, drop) -> {
            if (drop.server().equals(server)) {
                chunks.computeIfAbsent(chunkKey(drop), ignored -> new ConcurrentHashMap<>()).put(id, drop);
            }
        });
    }

    private synchronized void retireCache(final ProfileDocumentKey key) {
        if (players.getLoadedPlayers().stream().anyMatch(player -> sessions.activeContext(player)
            .map(context -> key(context).equals(key)).orElse(false) && connections.containsKey(player))) {
            return;
        }
        Map<UUID, WorldDrop> removed = indexes.remove(key);
        if (removed != null) {
            removed.forEach((id, drop) -> {
                removeChunk(id, drop);
                removeProjection(id);
            });
        }
    }

    private void removeProjection(final UUID id) {
        Item projection = projections.remove(id);
        if (projection != null) {
            schedules.runFor(projection, projection::remove);
        }
    }

    private void removeChunk(final UUID id, final WorldDrop drop) {
        ChunkKey key = chunkKey(drop);
        Map<UUID, WorldDrop> values = chunks.get(key);
        if (values != null) {
            values.remove(id);
            if (values.isEmpty()) {
                chunks.remove(key, values);
            }
        }
    }

    private static Map<UUID, WorldDrop> live(final Map<UUID, WorldDrop> drops, final long now) {
        var result = new LinkedHashMap<UUID, WorldDrop>();
        drops.forEach((id, drop) -> {
            if (drop.expiresAt() > now) {
                result.put(id, drop);
            }
        });
        return result;
    }

    private static void requireCurrent(final VexPlayer player, final PlayerProfileContext context) {
        if (!player.isCurrentContext(context)) {
            throw new IllegalStateException("Native drop's profile context retired");
        }
    }

    private static boolean canAccept(final PlayerInventory inventory, final ItemStack stack) {
        int maximum = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
        ItemStack[] contents = Objects.requireNonNull(inventory.getStorageContents(), "storage contents");
        for (ItemStack present : contents) {
            if (present == null || present.isEmpty() || present.isSimilar(stack) && present.getAmount() < maximum) {
                return true;
            }
        }
        return false;
    }

    private static void remove(final Player player, final List<ItemStack> originals) {
        ItemStack[] contents = Objects.requireNonNull(player.getInventory().getContents(), "inventory");
        for (int slot = 0; slot < contents.length; slot++) {
            contents[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        ItemStack cursor = player.getItemOnCursor();
        cursor = cursor == null ? null : cursor.clone();
        for (ItemStack original : originals) {
            int amount = original.getAmount();
            for (ItemStack available : contents) {
                if (available != null && available.isSimilar(original)) {
                    int taken = Math.min(available.getAmount(), amount);
                    available.setAmount(available.getAmount() - taken);
                    amount -= taken;
                }
            }
            if (amount > 0 && cursor != null && cursor.isSimilar(original)) {
                int taken = Math.min(cursor.getAmount(), amount);
                cursor.setAmount(cursor.getAmount() - taken);
                amount -= taken;
            }
            if (amount != 0) {
                throw new IllegalStateException("Dropped item is no longer in the source inventory");
            }
        }
        player.getInventory().setContents(contents);
        player.setItemOnCursor(cursor);
    }

    private static UUID identity(final Item item) {
        String value = item.getPersistentDataContainer().get(DROP_ID, PersistentDataType.STRING);
        return value == null ? null : UUID.fromString(value);
    }

    private static ProfileDocumentKey key(final PlayerProfileContext context) {
        return key(PlayerDataAddress.shared(context.domain(), context.profileId()));
    }

    private static ProfileDocumentKey key(final PlayerDataAddress address) {
        return new ProfileDocumentKey(address, "vexcore", "world_items");
    }

    private static int chunk(final double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static ChunkKey chunkKey(final WorldDrop drop) {
        return new ChunkKey(drop.position().world(), chunk(drop.position().x()), chunk(drop.position().z()));
    }

    private record Index(long revision, Map<UUID, WorldDrop> drops) {
    }

    private record ChunkKey(String world, int x, int z) {
    }

    private record Position(String world, double x, double y, double z) {

        private Position {
            WorldKey.parse(world);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Invalid world item position");
            }
        }
    }

    private record WorldDrop(String server, Position position, String stack, long expiresAt) {

        private WorldDrop {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(position, "position");
            if (stack == null || stack.isEmpty() || stack.length() > 1_048_576 || expiresAt <= 0) {
                throw new IllegalArgumentException("Invalid world item payload");
            }
        }
    }

    private record PublicLoot(String stack, long expiresAt) {

        private PublicLoot {
            if (stack == null || stack.length() > 1_048_576 || expiresAt <= 0) {
                throw new IllegalArgumentException("Invalid public loot receipt");
            }
        }
    }

    private record Journal(Map<UUID, WorldDrop> drops) {

        private Journal {
            drops = Map.copyOf(drops);
            if (drops.size() > MAXIMUM_DROPS) {
                throw new IllegalArgumentException("Invalid world journal capacity");
            }
        }
    }
}
