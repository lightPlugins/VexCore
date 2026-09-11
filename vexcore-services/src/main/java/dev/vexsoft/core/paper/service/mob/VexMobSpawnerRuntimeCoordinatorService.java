package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobHandle;
import dev.vexsoft.core.paper.mob.MobRemovalReason;
import dev.vexsoft.core.paper.mob.MobScope;
import dev.vexsoft.core.paper.mob.MobSpawnRequest;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnPositionRules;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerDefinition;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerKey;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerScope;
import dev.vexsoft.core.paper.nms.position.GroundPositionSafety;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/** Region-safe range activation and population runtime for mob spawner points. */
@Dependencies({MobRegistryCoordinatorService.class, MobRuntimeCoordinatorService.class, ScheduleService.class})
public final class VexMobSpawnerRuntimeCoordinatorService implements MobSpawnerRuntimeCoordinatorService, Listener,
    AutoCloseable {

    private static final UUID GLOBAL_POPULATION = new UUID(0L, 0L);
    private static final long PLAYER_PULSE_TICKS = 10L;
    private final Plugin plugin;
    private final MobRegistryCoordinatorService mobDefinitions;
    private final MobRuntimeCoordinatorService mobs;
    private final ScheduleService schedules;
    private final Map<MobSpawnerKey, SpawnerRuntime> spawners = new ConcurrentHashMap<>();
    private final Map<UUID, VexTask> playerTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<MobSpawnerKey>> playerInterests = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, Set<MobSpawnerKey>>> spatialIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> worldSearchChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Population> populationsByMob = new ConcurrentHashMap<>();
    private final MobRuntimeRemovalListener removalListener = this::onMobRemoved;
    private boolean started;

    public VexMobSpawnerRuntimeCoordinatorService(final VexServiceRegistry services) {
        Plugin ownerPlugin = Bukkit.getPluginManager().getPlugin("VexCore");

        if (ownerPlugin == null) {
            throw new IllegalStateException("VexCore plugin is unavailable");
        }

        plugin = ownerPlugin;
        mobDefinitions = services.require(MobRegistryCoordinatorService.class);
        mobs = services.require(MobRuntimeCoordinatorService.class);
        schedules = services.require(ScheduleService.class);
    }

    @Override
    public void register(final ServiceOwner owner, final MobSpawnerDefinition definition) {
        Objects.requireNonNull(owner, "owner");
        MobSpawnerDefinition checked = Objects.requireNonNull(definition, "definition");

        if (!mobDefinitions.owns(owner, checked.mobKey())) {
            throw new IllegalArgumentException(
                "Spawner mob definition is not owned by this plugin: " + checked.mobKey());
        }

        SpawnerRuntime replacement = new SpawnerRuntime(owner, checked);
        SpawnerRuntime previous = spawners.put(checked.key(), replacement);

        if (previous != null) {
            removeFromIndex(previous);
            deactivateAll(previous, MobRemovalReason.SPAWNER_RELOADED);
        }

        addToIndex(replacement);

        if (started) {
            Bukkit.getOnlinePlayers().forEach(this::requestPlayerPulse);
        }
    }

    @Override
    public void unregister(final ServiceOwner owner, final MobSpawnerKey key, final boolean reload) {
        SpawnerRuntime runtime = spawners.get(Objects.requireNonNull(key, "key"));

        if (runtime == null) {
            return;
        }

        if (!runtime.ownerName.equals(VexMobRegistryCoordinatorService.ownerName(owner))) {
            throw new IllegalArgumentException("Mob spawner is owned by another plugin: " + key);
        }

        if (spawners.remove(key, runtime)) {
            removeFromIndex(runtime);
            playerInterests.values().forEach(keys -> keys.remove(key));
            deactivateAll(runtime, reload ? MobRemovalReason.SPAWNER_RELOADED : MobRemovalReason.SPAWNER_REMOVED);
        }
    }

    @Override
    public void unregisterOwner(final ServiceOwner owner) {
        String ownerName = VexMobRegistryCoordinatorService.ownerName(owner);

        spawners.values()
            .stream()
            .filter(runtime -> runtime.ownerName.equals(ownerName))
            .toList()
            .forEach(runtime -> unregister(owner, runtime.definition.key(), false));
    }

    @Override
    public void start() {
        if (started) {
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        mobs.addRemovalListener(removalListener);
        started = true;
        Bukkit.getOnlinePlayers().forEach(this::startPlayer);
    }

    @Override
    public void shutdown() {
        if (started) {
            HandlerList.unregisterAll(this);
            started = false;
        }

        playerTasks.values().forEach(VexTask::cancel);
        playerTasks.clear();
        playerInterests.clear();
        spatialIndex.clear();
        worldSearchChunks.clear();
        populationsByMob.clear();
        mobs.removeRemovalListener(removalListener);
        spawners.values().forEach(runtime -> deactivateAll(runtime, MobRemovalReason.SERVER_SHUTDOWN));
    }

    @Override
    public void refresh(final Player player) {
        Player checked = Objects.requireNonNull(player, "player");

        startPlayer(checked);
        requestPlayerPulse(checked);
    }

    @Override
    public void close() {
        shutdown();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onJoin(final PlayerJoinEvent event) {
        startPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onQuit(final PlayerQuitEvent event) {
        stopPlayer(event.getPlayer().getUniqueId(), MobRemovalReason.OWNER_QUIT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onDeath(final PlayerDeathEvent event) {
        stopPlayer(event.getPlayer().getUniqueId(), MobRemovalReason.PLAYER_DEATH);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onRespawn(final PlayerRespawnEvent event) {
        startPlayer(event.getPlayer());
        requestPlayerPulse(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onTeleport(final PlayerTeleportEvent event) {
        if (!MobViewerTeleport.changesPosition(event)) {
            return;
        }

        deactivatePlayerInterests(event.getPlayer().getUniqueId(), MobRemovalReason.PLAYER_TELEPORT);
        schedules.runForLater(event.getPlayer(), 1L, () -> pulsePlayer(event.getPlayer()), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onWorldChange(final PlayerChangedWorldEvent event) {
        requestPlayerPulse(event.getPlayer());
    }

    private void startPlayer(final Player player) {
        playerTasks.computeIfAbsent(
            player.getUniqueId(),
            ignored -> schedules.runForTimer(
                player,
                1L,
                PLAYER_PULSE_TICKS,
                () -> pulsePlayer(player),
                () -> deactivatePlayerInterests(player.getUniqueId(), MobRemovalReason.OWNER_QUIT)
            ).orElse(null)
        );
        playerTasks.values().removeIf(Objects::isNull);
    }

    private void requestPlayerPulse(final Player player) {
        schedules.runFor(player, () -> pulsePlayer(player));
    }

    private void stopPlayer(final UUID playerId, final MobRemovalReason reason) {
        VexTask task = playerTasks.remove(playerId);

        if (task != null) {
            task.cancel();
        }

        deactivatePlayerInterests(playerId, reason);
    }

    private void pulsePlayer(final Player player) {
        if (!started) {
            return;
        }

        if (!eligible(player)) {
            deactivatePlayerInterests(player.getUniqueId(), MobRemovalReason.SPAWNER_DEACTIVATED);

            return;
        }

        Location playerLocation = player.getLocation();
        UUID playerId = player.getUniqueId();
        Set<MobSpawnerKey> keys = nearbySpawnerKeys(playerLocation);

        keys.addAll(playerInterests.getOrDefault(playerId, Set.of()));

        for (MobSpawnerKey key : keys) {
            SpawnerRuntime runtime = spawners.get(key);

            if (runtime == null) {
                removePlayerInterest(playerId, key);
                continue;
            }

            boolean interested = runtime.interestedPlayers.contains(playerId);
            boolean inRange = inRange(
                playerLocation,
                runtime.definition.anchor(),
                interested ? runtime.definition.deactivationRadius() : runtime.definition.activationRadius()
            );

            if (inRange) {
                runtime.interestedPlayers.add(playerId);
                playerInterests.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(runtime.definition.key());
                Population population = runtime.population(playerId);

                requestPopulationPulse(runtime, population);
            } else if (interested) {
                runtime.interestedPlayers.remove(playerId);
                removePlayerInterest(playerId, runtime.definition.key());

                if (runtime.definition.scope() == MobSpawnerScope.PER_PLAYER) {
                    Population removed = runtime.populations.remove(playerId);

                    deactivatePopulation(runtime, removed, MobRemovalReason.SPAWNER_DEACTIVATED);
                } else if (runtime.interestedPlayers.isEmpty()) {
                    Population global = runtime.populations.remove(GLOBAL_POPULATION);

                    deactivatePopulation(runtime, global, MobRemovalReason.SPAWNER_DEACTIVATED);
                }
            }
        }
    }

    private void deactivatePlayerInterests(final UUID playerId, final MobRemovalReason reason) {
        Set<MobSpawnerKey> keys = playerInterests.remove(playerId);

        if (keys == null) {
            return;
        }

        for (MobSpawnerKey key : List.copyOf(keys)) {
            SpawnerRuntime runtime = spawners.get(key);

            if (runtime == null) {
                continue;
            }

            if (!runtime.interestedPlayers.remove(playerId)) {
                continue;
            }

            if (runtime.definition.scope() == MobSpawnerScope.PER_PLAYER) {
                Population removed = runtime.populations.remove(playerId);

                deactivatePopulation(runtime, removed, reason);
            } else if (runtime.interestedPlayers.isEmpty()) {
                Population global = runtime.populations.remove(GLOBAL_POPULATION);

                deactivatePopulation(runtime, global, reason);
            }
        }
    }

    private Set<MobSpawnerKey> nearbySpawnerKeys(final Location location) {
        UUID worldId = location.getWorld().getUID();
        Map<Long, Set<MobSpawnerKey>> worldIndex = spatialIndex.get(worldId);

        if (worldIndex == null) {
            return new HashSet<>();
        }

        int radius = worldSearchChunks.getOrDefault(worldId, 0);
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        Set<MobSpawnerKey> result = new HashSet<>();

        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                result.addAll(worldIndex.getOrDefault(chunkKey(centerX + offsetX, centerZ + offsetZ), Set.of()));
            }
        }

        return result;
    }

    private void addToIndex(final SpawnerRuntime runtime) {
        Location anchor = runtime.definition.anchor();
        UUID worldId = anchor.getWorld().getUID();

        spatialIndex.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>())
            .computeIfAbsent(
                chunkKey(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4),
                ignored -> ConcurrentHashMap.newKeySet()
            )
            .add(runtime.definition.key());
        int chunks = (int) Math.ceil(runtime.definition.deactivationRadius() / 16.0D) + 1;

        worldSearchChunks.merge(worldId, chunks, Math::max);
    }

    private void removeFromIndex(final SpawnerRuntime runtime) {
        Location anchor = runtime.definition.anchor();
        UUID worldId = anchor.getWorld().getUID();
        Map<Long, Set<MobSpawnerKey>> worldIndex = spatialIndex.get(worldId);

        if (worldIndex != null) {
            long chunk = chunkKey(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4);
            Set<MobSpawnerKey> keys = worldIndex.get(chunk);

            if (keys != null) {
                keys.remove(runtime.definition.key());

                if (keys.isEmpty()) {
                    worldIndex.remove(chunk, keys);
                }
            }

            if (worldIndex.isEmpty()) {
                spatialIndex.remove(worldId, worldIndex);
            }
        }

        recalculateSearchRadius(worldId);
    }

    private void recalculateSearchRadius(final UUID worldId) {
        int chunks = spawners.values()
            .stream()
            .filter(runtime -> runtime.definition.anchor().getWorld().getUID().equals(worldId))
            .mapToInt(runtime -> (int) Math.ceil(runtime.definition.deactivationRadius() / 16.0D) + 1)
            .max()
            .orElse(0);

        if (chunks == 0) {
            worldSearchChunks.remove(worldId);
        } else {
            worldSearchChunks.put(worldId, chunks);
        }
    }

    private void removePlayerInterest(final UUID playerId, final MobSpawnerKey key) {
        Set<MobSpawnerKey> interests = playerInterests.get(playerId);

        if (interests != null) {
            interests.remove(key);

            if (interests.isEmpty()) {
                playerInterests.remove(playerId, interests);
            }
        }
    }

    private static long chunkKey(final int x, final int z) {
        return (long) x << 32 ^ z & 0xFFFFFFFFL;
    }

    private void requestPopulationPulse(final SpawnerRuntime runtime, final Population population) {
        if (!population.pulseQueued.compareAndSet(false, true)) {
            return;
        }

        schedules.runAt(
            runtime.definition.anchor(),
            () -> {
                population.pulseQueued.set(false);
                pulsePopulation(runtime, population);
            }
        );
    }

    private void pulsePopulation(final SpawnerRuntime runtime, final Population population) {
        if (!runtime.active(population.playerId) || !runtime.populations.containsValue(population)) {
            deactivatePopulation(runtime, population, MobRemovalReason.SPAWNER_DEACTIVATED);

            return;
        }

        if (population.handles.size() >= runtime.definition.maximumAlive()
            || Bukkit.getCurrentTick() < population.nextSpawnTick) {
            return;
        }

        MobDefinition mobDefinition = mobDefinitions.find(runtime.definition.mobKey()).orElse(null);

        if (mobDefinition == null) {
            population.nextSpawnTick = Bukkit.getCurrentTick() + runtime.definition.spawnIntervalTicks();

            return;
        }

        int amount = Math.min(
            runtime.definition.spawnBatchSize(),
            runtime.definition.maximumAlive() - population.handles.size()
        );

        for (int index = 0; index < amount; index++) {
            Location spawnLocation = findSpawnLocation(runtime.definition);

            if (spawnLocation == null) {
                break;
            }

            MobScope scope = population.playerId.equals(GLOBAL_POPULATION) ? MobScope.global()
                : MobScope.player(population.playerId);
            MobHandle handle = mobs.spawn(
                runtime.owner,
                mobDefinition,
                new MobSpawnRequest(mobDefinition.key(), spawnLocation, scope, runtime.definition.anchor())
            );

            population.handles.add(handle);
            populationsByMob.put(handle.instanceId(), population);
        }

        population.nextSpawnTick = Bukkit.getCurrentTick() + runtime.definition.spawnIntervalTicks();
    }

    private Location findSpawnLocation(final MobSpawnerDefinition definition) {
        Location anchor = definition.anchor();
        World world = anchor.getWorld();

        if (definition.exactSpawnPosition()) {
            return world.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4) ? anchor : null;
        }

        MobSpawnPositionRules rules = definition.positionRules();

        for (int attempt = 0; attempt < rules.attempts(); attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            double distance = rules.maximumRadius() == rules.minimumRadius() ? rules.minimumRadius()
                : ThreadLocalRandom.current().nextDouble(rules.minimumRadius(), rules.maximumRadius());
            double x = anchor.getX() + Math.cos(angle) * distance;
            double z = anchor.getZ() + Math.sin(angle) * distance;
            int blockX = (int) Math.floor(x);
            int blockZ = (int) Math.floor(z);

            if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                continue;
            }

            Location result = findGround(world, anchor, x, z, rules);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private Location findGround(
        final World world,
        final Location anchor,
        final double x,
        final double z,
        final MobSpawnPositionRules rules
    ) {
        int minimumY = Math.max(world.getMinHeight() + 1, anchor.getBlockY() + rules.minimumYOffset());
        int maximumY =
            Math.min(world.getMaxHeight() - rules.clearanceBlocks(), anchor.getBlockY() + rules.maximumYOffset());

        for (int offset = 0; offset <= maximumY - minimumY; offset++) {
            int upper = anchor.getBlockY() + offset;

            if (upper >= minimumY && upper <= maximumY && validPosition(world, x, upper, z, rules)) {
                return centered(world, x, upper, z, anchor);
            }

            int lower = anchor.getBlockY() - offset;

            if (offset > 0 && lower >= minimumY && lower <= maximumY && validPosition(world, x, lower, z, rules)) {
                return centered(world, x, lower, z, anchor);
            }
        }

        return null;
    }

    private boolean validPosition(
        final World world,
        final double x,
        final int y,
        final double z,
        final MobSpawnPositionRules rules
    ) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);

        return GroundPositionSafety.isSafe(
            world,
            blockX,
            y,
            blockZ,
            rules.clearanceBlocks(),
            rules.avoidFluids(),
            rules.avoidHazards(),
            rules.allowedSupportBlocks(),
            rules.deniedSupportBlocks()
        );
    }

    private void deactivateAll(final SpawnerRuntime runtime, final MobRemovalReason reason) {
        runtime.interestedPlayers.clear();
        Collection<Population> populations = List.copyOf(runtime.populations.values());

        runtime.populations.clear();
        populations.forEach(population -> deactivatePopulation(runtime, population, reason));
    }

    private void deactivatePopulation(
        final SpawnerRuntime runtime,
        final Population population,
        final MobRemovalReason reason
    ) {
        if (population == null || !population.deactivating.compareAndSet(false, true)) {
            return;
        }

        for (MobHandle handle : List.copyOf(population.handles)) {
            mobs.removeSafely(runtime.owner, handle, reason);
        }
    }

    private void onMobRemoved(final MobHandle handle, final MobRemovalReason reason) {
        Population population = populationsByMob.remove(handle.instanceId());

        if (population != null) {
            population.handles.remove(handle);
        }
    }

    private static boolean eligible(final Player player) {
        return player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static boolean inRange(final Location player, final Location anchor, final double radius) {
        return player.getWorld() == anchor.getWorld() && player.distanceSquared(anchor) <= radius * radius;
    }

    private static Location centered(
        final World world,
        final double x,
        final int y,
        final double z,
        final Location anchor
    ) {
        return new Location(world, Math.floor(x) + 0.5D, y, Math.floor(z) + 0.5D, anchor.getYaw(), anchor.getPitch());
    }

    private static final class SpawnerRuntime {

        private final ServiceOwner owner;
        private final String ownerName;
        private final MobSpawnerDefinition definition;
        private final Set<UUID> interestedPlayers = ConcurrentHashMap.newKeySet();
        private final Map<UUID, Population> populations = new ConcurrentHashMap<>();

        private SpawnerRuntime(final ServiceOwner owner, final MobSpawnerDefinition definition) {
            this.owner = owner;
            this.ownerName = VexMobRegistryCoordinatorService.ownerName(owner);
            this.definition = definition;
        }

        private Population population(final UUID playerId) {
            UUID key = definition.scope() == MobSpawnerScope.GLOBAL ? GLOBAL_POPULATION : playerId;

            return populations.computeIfAbsent(key, Population::new);
        }

        private boolean active(final UUID populationPlayerId) {
            return populationPlayerId.equals(GLOBAL_POPULATION) ? !interestedPlayers.isEmpty()
                : interestedPlayers.contains(populationPlayerId);
        }
    }

    private static final class Population {

        private final UUID playerId;
        private final Set<MobHandle> handles = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean pulseQueued = new AtomicBoolean();
        private final AtomicBoolean deactivating = new AtomicBoolean();
        private long nextSpawnTick;

        private Population(final UUID playerId) {
            this.playerId = playerId;
        }
    }
}
