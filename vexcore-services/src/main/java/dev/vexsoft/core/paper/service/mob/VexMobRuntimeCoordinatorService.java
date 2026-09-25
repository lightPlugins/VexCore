package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.MobDamageResult;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobHandle;
import dev.vexsoft.core.paper.mob.MobHologramDefinition;
import dev.vexsoft.core.paper.mob.MobKey;
import dev.vexsoft.core.paper.mob.MobRemovalReason;
import dev.vexsoft.core.paper.mob.MobScope;
import dev.vexsoft.core.paper.mob.MobSnapshot;
import dev.vexsoft.core.paper.mob.MobSpawnRequest;
import dev.vexsoft.core.paper.mob.event.MobDamageEvent;
import dev.vexsoft.core.paper.mob.event.MobRemovedEvent;
import dev.vexsoft.core.paper.mob.goal.LookAtPlayerGoalDefinition;
import dev.vexsoft.core.paper.mob.goal.MobGoalDefinition;
import dev.vexsoft.core.paper.mob.goal.OwnerMeleeGoalDefinition;
import dev.vexsoft.core.paper.mob.goal.RandomMovementGoalDefinition;
import dev.vexsoft.core.paper.nms.goal.NmsLookAtPlayerSpec;
import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import dev.vexsoft.core.paper.nms.goal.NmsOwnerMeleeSpec;
import dev.vexsoft.core.paper.nms.goal.NmsMeleeLeapSpec;
import dev.vexsoft.core.paper.nms.goal.NmsMeleeRangedSpec;
import dev.vexsoft.core.paper.nms.goal.NmsRandomMovementSpec;
import dev.vexsoft.core.paper.nms.service.NmsMobAdapterService;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.packets.display.DisplayTransformation;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayUpdate;
import dev.vexsoft.core.paper.packets.service.DisplayPassengerPacketService;
import dev.vexsoft.core.paper.packets.service.MobGlowPacketService;
import dev.vexsoft.core.paper.packets.service.MobHitPacketService;
import dev.vexsoft.core.paper.packets.service.TextDisplayPacketService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/** Default custom mob runtime with opt-in native goals and viewer-specific presentation. */
@Dependencies({NmsMobAdapterService.class, TextDisplayPacketService.class, DisplayPassengerPacketService.class, MobGlowPacketService.class, MobHitPacketService.class, ScheduleService.class})
public final class VexMobRuntimeCoordinatorService implements MobRuntimeCoordinatorService, Listener, AutoCloseable {

    private static final double DEFAULT_ATTACK_KNOCKBACK = 0.4D;

    private final Plugin plugin;
    private final NmsMobAdapterService nms;
    private final TextDisplayPacketService textDisplays;
    private final DisplayPassengerPacketService passengers;
    private final MobGlowPacketService glows;
    private final MobHitPacketService hits;
    private final ScheduleService schedules;
    private final Map<UUID, RuntimeMob> mobs = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byEntity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> viewerEpochs = new ConcurrentHashMap<>();
    private final Set<MobRuntimeRemovalListener> removalListeners = ConcurrentHashMap.newKeySet();
    private boolean started;

    public VexMobRuntimeCoordinatorService(final VexServiceRegistry services) {
        Plugin ownerPlugin = Bukkit.getPluginManager().getPlugin("VexCore");

        if (ownerPlugin == null) {
            throw new IllegalStateException("VexCore plugin is unavailable");
        }

        plugin = ownerPlugin;
        nms = services.require(NmsMobAdapterService.class);
        textDisplays = services.require(TextDisplayPacketService.class);
        passengers = services.require(DisplayPassengerPacketService.class);
        glows = services.require(MobGlowPacketService.class);
        hits = services.require(MobHitPacketService.class);
        schedules = services.require(ScheduleService.class);
    }

    @Override
    public MobHandle spawn(final ServiceOwner owner, final MobDefinition definition, final MobSpawnRequest request) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(definition, "definition");
        MobSpawnRequest checkedRequest = Objects.requireNonNull(request, "request");
        Entity spawned =
            checkedRequest.location().getWorld().spawnEntity(checkedRequest.location(), definition.entityType());

        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            throw new IllegalArgumentException(
                "Custom mob carrier must implement Bukkit Mob: " + definition.entityType());
        }

        try {
            configureCarrier(mob, definition);
            definition.initializer().ifPresent(initializer -> initializer.initialize(mob));
        } catch (RuntimeException exception) {
            mob.remove();
            throw exception;
        }

        MobHandle handle = new MobHandle(UUID.randomUUID(), definition.key());
        RuntimeMob runtime = new RuntimeMob(
            VexMobRegistryCoordinatorService.ownerName(owner),
            handle,
            definition,
            checkedRequest.scope(),
            mob,
            definition.maxHealth(),
            definition.scale(),
            definition.glow().orElse(null)
        );

        mobs.put(handle.instanceId(), runtime);
        byEntity.put(mob.getUniqueId(), handle.instanceId());
        installGoals(runtime, checkedRequest.movementOrigin());
        applyScope(runtime);
        refreshPresentation(runtime);

        return handle;
    }

    @Override
    public Optional<MobSnapshot> find(final ServiceOwner owner, final MobHandle handle) {
        RuntimeMob runtime = mobs.get(Objects.requireNonNull(handle, "handle").instanceId());

        return runtime == null || !owned(owner, runtime) ? Optional.empty() : Optional.of(snapshot(runtime));
    }

    @Override
    public Optional<MobSnapshot> find(final Entity entity) {
        RuntimeMob runtime = findRuntime(Objects.requireNonNull(entity, "entity"));

        return runtime == null ? Optional.empty() : Optional.of(snapshot(runtime));
    }

    @Override
    public Collection<MobSnapshot> getActiveMobs(final ServiceOwner owner) {
        String ownerName = VexMobRegistryCoordinatorService.ownerName(owner);

        return mobs.values()
            .stream()
            .filter(runtime -> runtime.ownerName.equals(ownerName))
            .map(this::snapshot)
            .toList();
    }

    @Override
    public MobDamageResult damage(final ServiceOwner owner, final MobHandle handle, final double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException("amount must be finite and greater than zero");
        }

        RuntimeMob runtime = requireOwned(owner, handle);

        return damage(runtime, amount, null);
    }

    @Override
    public MobSnapshot setVelocity(final ServiceOwner owner, final MobHandle handle, final Vector velocity) {
        Vector checkedVelocity = Objects.requireNonNull(velocity, "velocity").clone();

        checkedVelocity.checkFinite();
        RuntimeMob runtime = requireOwned(owner, handle);
        // Goal-less carriers are neutralized with NoAI during spawn. Re-enable their native tick
        // before applying an explicit launch so Paper actually advances the requested velocity.
        if (runtime.definition.goals().isEmpty()) {
            nms.activateGoals(runtime.entity);
        }

        runtime.entity.setVelocity(checkedVelocity);

        return snapshot(runtime);
    }

    @Override
    public MobSnapshot setHealth(final ServiceOwner owner, final MobHandle handle, final double health) {
        if (!Double.isFinite(health)) {
            throw new IllegalArgumentException("health must be finite");
        }

        RuntimeMob runtime = requireOwned(owner, handle);

        runtime.health = Math.max(0.0D, Math.min(runtime.definition.maxHealth(), health));

        if (runtime.health == 0.0D) {
            removeRuntime(runtime, MobRemovalReason.DEATH);
        } else {
            synchronizeCarrierHealth(runtime);
            refreshPresentation(runtime);
        }

        return snapshot(runtime);
    }

    @Override
    public void setGoalsPaused(final ServiceOwner owner, final MobHandle handle, final boolean paused) {
        RuntimeMob runtime = requireOwned(owner, handle);
        NmsMobGoalControl.setPaused(runtime.entity, paused);

        if (paused) {
            runtime.entity.setTarget(null);
            runtime.entity.getPathfinder().stopPathfinding();
        }
    }

    @Override
    public MobSnapshot setScale(final ServiceOwner owner, final MobHandle handle, final double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("scale must be finite and greater than zero");
        }

        RuntimeMob runtime = requireOwned(owner, handle);

        setAttribute(runtime.entity, Attribute.SCALE, scale);
        runtime.scale = scale;
        refreshPresentation(runtime);

        return snapshot(runtime);
    }

    @Override
    public MobSnapshot setGlow(final ServiceOwner owner, final MobHandle handle, final DisplayGlowColor color) {
        RuntimeMob runtime = requireOwned(owner, handle);

        runtime.glow = Objects.requireNonNull(color, "color");
        refreshPresentation(runtime);

        return snapshot(runtime);
    }

    @Override
    public MobSnapshot clearGlow(final ServiceOwner owner, final MobHandle handle) {
        RuntimeMob runtime = requireOwned(owner, handle);

        runtime.glow = null;
        refreshPresentation(runtime);

        return snapshot(runtime);
    }

    @Override
    public void refreshPresentation(final ServiceOwner owner, final MobHandle handle) {
        refreshPresentation(requireOwned(owner, handle));
    }

    @Override
    public boolean remove(final ServiceOwner owner, final MobHandle handle, final MobRemovalReason reason) {
        RuntimeMob runtime = mobs.get(Objects.requireNonNull(handle, "handle").instanceId());

        if (runtime == null) {
            return false;
        }

        if (!owned(owner, runtime)) {
            throw new IllegalArgumentException("Mob instance is owned by another plugin: " + handle);
        }

        removeRuntime(runtime, Objects.requireNonNull(reason, "reason"));

        return true;
    }

    @Override
    public void removeSafely(final ServiceOwner owner, final MobHandle handle, final MobRemovalReason reason) {
        RuntimeMob runtime = mobs.get(Objects.requireNonNull(handle, "handle").instanceId());

        if (runtime == null) {
            return;
        }

        if (!owned(owner, runtime)) {
            throw new IllegalArgumentException("Mob instance is owned by another plugin: " + handle);
        }

        schedules.runFor(
            runtime.entity,
            () -> removeRuntime(runtime, reason),
            () -> {
                removeRuntime(runtime, MobRemovalReason.INVALID_ENTITY);
            }
        );
    }

    @Override
    public int removeAll(final ServiceOwner owner, final MobRemovalReason reason) {
        String ownerName = VexMobRegistryCoordinatorService.ownerName(owner);
        Collection<RuntimeMob> ownedMobs =
            mobs.values().stream().filter(runtime -> runtime.ownerName.equals(ownerName)).toList();

        ownedMobs.forEach(runtime -> removeRuntime(runtime, reason));

        return ownedMobs.size();
    }

    @Override
    public int removeDefinition(final ServiceOwner owner, final MobKey key) {
        String ownerName = VexMobRegistryCoordinatorService.ownerName(owner);
        Collection<RuntimeMob> matchingMobs = mobs.values()
            .stream()
            .filter(runtime -> runtime.ownerName.equals(ownerName))
            .filter(runtime -> runtime.definition.key().equals(key))
            .toList();

        matchingMobs.forEach(runtime -> removeRuntime(runtime, MobRemovalReason.DEFINITION_REMOVED));

        return matchingMobs.size();
    }

    @Override
    public void addRemovalListener(final MobRuntimeRemovalListener listener) {
        removalListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeRemovalListener(final MobRuntimeRemovalListener listener) {
        removalListeners.remove(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void start() {
        if (!started) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            started = true;
        }
    }

    @Override
    public void shutdown() {
        if (started) {
            HandlerList.unregisterAll(this);
            started = false;
        }

        mobs.values().stream().toList().forEach(runtime -> removeRuntime(runtime, MobRemovalReason.SERVER_SHUTDOWN));
    }

    @Override
    public void close() {
        shutdown();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onDamage(final EntityDamageEvent event) {
        RuntimeMob runtime = findRuntime(event.getEntity());

        if (runtime == null) {
            return;
        }

        event.setCancelled(true);

        if (!mayInteract(runtime, event.getDamageSource().getCausingEntity())) {
            return;
        }

        double charge = 1.0D;
        AttackCharge sample = runtime.attackCharge;

        if (sample != null && sample.tick() == Bukkit.getCurrentTick()
            && event instanceof EntityDamageByEntityEvent attack && attack.getDamager() instanceof Player player
            && sample.player().equals(player.getUniqueId())) {
            charge = sample.value();
            runtime.attackCharge = null;
        }

        Entity attacker = event.getDamageSource().getCausingEntity();
        MobDamageResult result = damage(runtime, event.getFinalDamage(), attacker, charge);

        if (result.applied() && !result.killed() && event instanceof EntityDamageByEntityEvent attack) {
            applyKnockback(runtime.entity, attacker == null ? attack.getDamager() : attacker,
                attack.getDamager());
        }
    }

    private static void applyKnockback(final Mob victim, final Entity source, final Entity directSource) {
        if (!source.getWorld().equals(victim.getWorld())) {
            return;
        }

        Location victimLocation = victim.getLocation();
        Location sourceLocation = source.getLocation();
        double directionX = sourceLocation.getX() - victimLocation.getX();
        double directionZ = sourceLocation.getZ() - victimLocation.getZ();

        if (directionX * directionX + directionZ * directionZ < 0.000001D
            && directSource instanceof Projectile projectile) {
            Vector velocity = projectile.getVelocity();
            directionX = -velocity.getX();
            directionZ = -velocity.getZ();
        }

        if (directionX * directionX + directionZ * directionZ < 0.000001D) {
            Vector facing = sourceLocation.getDirection();
            directionX = -facing.getX();
            directionZ = -facing.getZ();
        }

        if (directionX * directionX + directionZ * directionZ >= 0.000001D) {
            victim.knockback(DEFAULT_ATTACK_KNOCKBACK, directionX, directionZ);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onRegainHealth(final EntityRegainHealthEvent event) {
        if (findRuntime(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onAttackCharge(final PrePlayerAttackEntityEvent event) {
        RuntimeMob runtime = findRuntime(event.getAttacked());

        if (runtime != null) {
            runtime.attackCharge = new AttackCharge(
                event.getPlayer().getUniqueId(),
                Bukkit.getCurrentTick(),
                Math.clamp(event.getPlayer().getAttackCooldown(), 0.0D, 1.0D)
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onDeath(final EntityDeathEvent event) {
        RuntimeMob runtime = findRuntime(event.getEntity());

        if (runtime != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            removeRuntime(runtime, MobRemovalReason.DEATH, runtime.deathAttacker, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onTrack(final PlayerTrackEntityEvent event) {
        RuntimeMob runtime = findRuntime(event.getEntity());

        if (runtime != null && runtime.scope.includes(event.getPlayer().getUniqueId())) {
            Player viewer = event.getPlayer();
            clearPresentation(runtime, viewer);
            long epoch = viewerEpochs.getOrDefault(viewer.getUniqueId(), 0L);
            // Paper fires this event before addPairing sends the carrier spawn packet.
            // Defer both display spawn and mount; getTrackedBy is already populated here.
            runtime.presentationGate.defer(viewer.getUniqueId(),
                (ready, retired) -> schedules.runForLater(runtime.entity, 1L, ready, retired),
                () -> viewer.isOnline() && !runtime.removing
                    && mobs.get(runtime.handle.instanceId()) == runtime
                    && viewerEpochs.getOrDefault(viewer.getUniqueId(), 0L) == epoch,
                () -> showPresentation(runtime, viewer));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onUntrack(final PlayerUntrackEntityEvent event) {
        RuntimeMob runtime = findRuntime(event.getEntity());

        if (runtime != null) {
            clearPresentation(runtime, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onJoin(final PlayerJoinEvent event) {
        Player player = event.getPlayer();

        viewerEpochs.putIfAbsent(player.getUniqueId(), 0L);
        mobs.values().forEach(runtime -> applyScope(runtime, player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onQuit(final PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        invalidateViewer(event.getPlayer());
        viewerEpochs.remove(playerId);
        mobs.values()
            .stream()
            .filter(runtime -> runtime.scope.playerId().filter(playerId::equals).isPresent())
            .toList()
            .forEach(runtime -> removeRuntime(runtime, MobRemovalReason.OWNER_QUIT));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onWorldChange(final PlayerChangedWorldEvent event) {
        transitionViewer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerDeath(final PlayerDeathEvent event) {
        Player player = event.getPlayer();

        invalidateViewer(player);
        UUID playerId = player.getUniqueId();

        mobs.values()
            .stream()
            .filter(runtime -> runtime.scope.playerId().filter(playerId::equals).isPresent())
            .toList()
            .forEach(runtime -> removeRuntime(runtime, MobRemovalReason.PLAYER_DEATH));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onRespawn(final PlayerRespawnEvent event) {
        transitionViewer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onEntitiesUnload(final EntitiesUnloadEvent event) {
        event.getEntities()
            .stream()
            .map(this::findRuntime)
            .filter(Objects::nonNull)
            .toList()
            .forEach(runtime -> removeRuntime(runtime, MobRemovalReason.CHUNK_UNLOAD));
    }

    private void configureCarrier(final Mob mob, final MobDefinition definition) {
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.setSilent(definition.silent());
        mob.setGravity(definition.gravity());
        mob.setCollidable(definition.collidable());
        mob.setCustomNameVisible(false);
        mob.setGlowing(false);
        mob.setInvulnerable(false);
        mob.setTarget(null);
        mob.getActivePotionEffects().forEach(effect -> mob.removePotionEffect(effect.getType()));
        EntityEquipment equipment = mob.getEquipment();

        if (equipment != null) {
            equipment.clear();
        }

        if (mob instanceof Ageable ageable) {
            if (definition.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }

            ageable.setAgeLock(true);
        }

        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);

        if (maxHealth == null) {
            throw new IllegalArgumentException("Carrier has no maximum-health attribute");
        }

        maxHealth.setBaseValue(20.0D);
        setAttribute(mob, Attribute.SCALE, definition.scale());
        setAttribute(mob, Attribute.KNOCKBACK_RESISTANCE, definition.knockbackResistance());

        if (definition.movementSpeed() > 0.0D) {
            setAttribute(mob, Attribute.MOVEMENT_SPEED, definition.movementSpeed());
        }

        mob.setHealth(maxHealth.getValue());
        nms.neutralize(mob);
    }

    private void synchronizeCarrierHealth(final RuntimeMob runtime) {
        AttributeInstance maximum = runtime.entity.getAttribute(Attribute.MAX_HEALTH);

        if (maximum == null || runtime.health <= 0.0D || runtime.entity.isDead()) {
            return;
        }

        double displayed = maximum.getValue() * runtime.health / runtime.definition.maxHealth();
        runtime.entity.setHealth(Math.clamp(displayed, 0.01D, maximum.getValue()));
    }

    private void installGoals(final RuntimeMob runtime, final Location origin) {
        for (MobGoalDefinition goal : runtime.definition.goals()) {
            if (goal instanceof RandomMovementGoalDefinition random) {
                nms.addRandomMovement(
                    runtime.entity,
                    new NmsRandomMovementSpec(
                        random.priority(),
                        random.speed(),
                        random.minimumDistance(),
                        random.maximumDistance(),
                        random.leashRadius(),
                        random.minimumYOffset(),
                        random.maximumYOffset(),
                        random.attempts(),
                        random.minimumIdleTicks(),
                        random.maximumIdleTicks(),
                        random.retryDelayTicks(),
                        random.constrainEntirePath(),
                        random.avoidFluids(),
                        random.avoidHazards(),
                        random.allowedSupportBlocks(),
                        random.deniedSupportBlocks(),
                        origin
                    )
                );
            } else if (goal instanceof LookAtPlayerGoalDefinition look) {
                nms.addLookAtPlayer(
                    runtime.entity,
                    new NmsLookAtPlayerSpec(
                        look.priority(),
                        look.acquireRadius(),
                        look.releaseRadius(),
                        look.reacquireIntervalTicks(),
                        look.minimumTargetLockTicks(),
                        look.switchDistanceAdvantage(),
                        look.requireLineOfSight(),
                        look.whileMoving(),
                        look.yawOnly(),
                        runtime.definition.rotationSpeed(),
                        runtime.scope.playerId().orElse(null)
                    )
                );
            } else if (goal instanceof OwnerMeleeGoalDefinition melee) {
                nms.addOwnerMelee(
                    runtime.entity,
                    new NmsOwnerMeleeSpec(
                        melee.priority(),
                        melee.speed(),
                        melee.radius(),
                        melee.reach(),
                        melee.damage(),
                        melee.attackIntervalTicks(),
                        melee.pathIntervalTicks(),
                        runtime.scope.playerId().orElse(null),
                        new NmsMeleeLeapSpec(
                            melee.leap().enabled(), melee.leap().minDistance(), melee.leap().cooldownTicks(),
                            melee.leap().horizontalSpeed(), melee.leap().maxHorizontalSpeed(), melee.leap().verticalSpeed(),
                            melee.leap().waterEnabled(), melee.leap().waterVerticalSpeed(),
                            melee.leap().waterHorizontalMultiplier(), melee.leap().swimSpeedMultiplier()),
                        new NmsMeleeRangedSpec(melee.ranged().enabled(), melee.ranged().stuckTicks(),
                            melee.ranged().intervalTicks(), melee.ranged().speed(), melee.ranged().lifetimeTicks()),
                        melee.pursuitSpreadRadius(),
                        melee.aggroRadius()
                    )
                );
            } else {
                throw new IllegalArgumentException("Unsupported mob goal: " + goal.getClass().getName());
            }
        }

        if (!runtime.definition.goals().isEmpty()) {
            nms.activateGoals(runtime.entity);
        }
    }

    private void applyScope(final RuntimeMob runtime) {
        Bukkit.getOnlinePlayers().forEach(player -> applyScope(runtime, player));
    }

    private void applyScope(final RuntimeMob runtime, final Player player) {
        if (runtime.scope.includes(player.getUniqueId())) {
            player.showEntity(plugin, runtime.entity);

            if (runtime.entity.getTrackedBy().contains(player)) {
                showPresentation(runtime, player);
            }
        } else {
            clearPresentation(runtime, player);
            player.hideEntity(plugin, runtime.entity);
        }
    }

    private void refreshPresentation(final RuntimeMob runtime) {
        Bukkit.getOnlinePlayers()
            .stream()
            .filter(player -> runtime.scope.includes(player.getUniqueId()))
            .filter(player -> runtime.entity.getTrackedBy().contains(player))
            .forEach(player -> showPresentation(runtime, player));
    }

    private void showPresentation(final RuntimeMob runtime, final Player viewer) {
        if (runtime.presentationGate.isPending(viewer.getUniqueId())
            || !runtime.entity.isValid() || runtime.entity.getWorld() != viewer.getWorld()
            || !runtime.entity.getTrackedBy().contains(viewer) || !runtime.scope.includes(viewer.getUniqueId())) {
            return;
        }

        long epoch = viewerEpochs.getOrDefault(viewer.getUniqueId(), 0L);

        if (runtime.glow == null) {
            glows.clearGlow(viewer, runtime.entity);
        } else {
            glows.setGlow(viewer, runtime.entity, runtime.glow);
        }

        Optional<MobHologramDefinition> hologram = runtime.definition.hologram();

        if (hologram.isEmpty()) {
            clearHologram(runtime, viewer);

            return;
        }

        HologramSession existing = runtime.holograms.get(viewer.getUniqueId());

        if (existing != null && existing.epoch != epoch) {
            clearHologram(runtime, viewer);
            existing = null;
        }

        if (existing == null) {
            Location hologramLocation = MobHologramAttachment.uprightLocation(runtime.entity.getLocation());

            FakeTextDisplayRequest.FakeTextDisplayRequestBuilder requestBuilder =
                FakeTextDisplayRequest.builder(
                        hologramLocation,
                        hologram.get().renderer().render(viewer, snapshot(runtime))
                    )
                    .billboard(hologram.get().billboard())
                    .backgroundColor(hologram.get().backgroundColor())
                    .defaultBackground(hologram.get().defaultBackground())
                    .shadowed(hologram.get().shadowed())
                    .seeThrough(hologram.get().seeThrough())
                    .lineWidth(hologram.get().lineWidth())
                    // Include the height in the initial metadata bundle, not a later offset packet.
                    .transformation(DisplayTransformation.scale(hologram.get().scale()).toBuilder()
                        .translationY(hologramOffset(runtime, hologram.get())).build())
                    .lifecycle(Set.<DisplayLifecycle>of());

            hologram.get().brightness().ifPresent(requestBuilder::brightness);
            FakeTextDisplayRequest request = requestBuilder.build();
            FakeDisplayHandle created = textDisplays.spawn(viewer, request);

            runtime.holograms.put(viewer.getUniqueId(), new HologramSession(created, epoch));
            MobHologramAttachment.attach(
                viewer,
                runtime.entity,
                created,
                hologramOffset(runtime, hologram.get()),
                passengers,
                textDisplays
            );
        } else {
            textDisplays.update(
                existing.handle,
                FakeTextDisplayUpdate.text(hologram.get().renderer().render(viewer, snapshot(runtime)))
            );
        }
    }

    private void clearPresentation(final RuntimeMob runtime, final Player viewer) {
        runtime.presentationGate.cancel(viewer.getUniqueId());
        clearHologram(runtime, viewer);
        glows.clearGlow(viewer, runtime.entity);
    }

    private void clearHologram(final RuntimeMob runtime, final Player viewer) {
        HologramSession session = runtime.holograms.remove(viewer.getUniqueId());

        if (session != null) {
            passengers.removeFakePassenger(viewer, runtime.entity, session.handle);
            textDisplays.remove(session.handle);
        }
    }

    private void transitionViewer(final Player player) {
        long epoch = invalidateViewer(player);

        schedules.runForLater(player, 1L, () -> reconcileViewer(player, epoch), null);
    }

    private long invalidateViewer(final Player player) {
        long epoch = viewerEpochs.merge(player.getUniqueId(), 1L, Long::sum);

        mobs.values().forEach(runtime -> clearPresentation(runtime, player));

        return epoch;
    }

    private void reconcileViewer(final Player player, final long expectedEpoch) {
        if (!started || !player.isOnline() || viewerEpochs.getOrDefault(player.getUniqueId(), 0L) != expectedEpoch) {
            return;
        }

        mobs.values().forEach(runtime -> applyScope(runtime, player));
    }

    private float hologramOffset(final RuntimeMob runtime, final MobHologramDefinition hologram) {
        return hologram.automaticOffset() ? (float) runtime.entity.getHeight() + hologram.offsetY()
            : hologram.offsetY();
    }

    private MobDamageResult damage(final RuntimeMob runtime, final double amount, final Entity attacker) {
        return damage(runtime, amount, attacker, 1.0D);
    }

    private MobDamageResult damage(
        final RuntimeMob runtime,
        final double amount,
        final Entity attacker,
        final double attackCharge
    ) {
        double previous = runtime.health;

        if (!runtime.entity.isValid() || runtime.removing) {
            return MobDamageResult.rejected(previous);
        }

        MobDamageEvent event = new MobDamageEvent(snapshot(runtime), attacker, amount, attackCharge);

        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled() || event.getDamage() == 0.0D || runtime.removing) {
            return MobDamageResult.rejected(runtime.health);
        }

        runtime.health = Math.max(0.0D, runtime.health - event.getDamage());
        boolean killed = runtime.health == 0.0D;

        if (killed) {
            runtime.deathAttacker = attacker;
            runtime.entity.setHealth(0.0D);
        } else {
            synchronizeCarrierHealth(runtime);
            Bukkit.getOnlinePlayers()
                .stream()
                .filter(player -> runtime.scope.includes(player.getUniqueId()))
                .filter(player -> player.getWorld().equals(runtime.entity.getWorld()))
                .forEach(player -> hits.playHit(player, runtime.entity));
            refreshPresentation(runtime);
        }

        return new MobDamageResult(true, killed, previous, runtime.health);
    }

    private void removeRuntime(final RuntimeMob runtime, final MobRemovalReason reason) {
        removeRuntime(runtime, reason, null);
    }

    private void removeRuntime(final RuntimeMob runtime, final MobRemovalReason reason, final Entity attacker) {
        removeRuntime(runtime, reason, attacker, true);
    }

    private void removeRuntime(
        final RuntimeMob runtime,
        final MobRemovalReason reason,
        final Entity attacker,
        final boolean removeCarrier
    ) {
        if (runtime.removing || mobs.remove(runtime.handle.instanceId(), runtime) == false) {
            return;
        }

        runtime.removing = true;
        NmsMobGoalControl.setPaused(runtime.entity, false);
        byEntity.remove(runtime.entity.getUniqueId(), runtime.handle.instanceId());
        nms.deactivateGoals(runtime.entity);
        Bukkit.getOnlinePlayers().forEach(player -> clearPresentation(runtime, player));
        runtime.holograms.clear();

        if (removeCarrier && runtime.entity.isValid()) {
            runtime.entity.remove();
        }

        removalListeners.forEach(listener -> listener.onRemoved(runtime.handle, reason));
        Bukkit.getPluginManager().callEvent(new MobRemovedEvent(snapshot(runtime), reason, attacker));
    }

    private boolean mayInteract(final RuntimeMob runtime, final Entity source) {
        if (runtime.scope.isGlobal() || source == null) {
            return true;
        }

        Entity resolved = source;

        if (source instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();

            if (shooter instanceof Entity shooterEntity) {
                resolved = shooterEntity;
            }
        }

        return resolved instanceof Player player && runtime.scope.includes(player.getUniqueId());
    }

    private RuntimeMob requireOwned(final ServiceOwner owner, final MobHandle handle) {
        RuntimeMob runtime = mobs.get(Objects.requireNonNull(handle, "handle").instanceId());

        if (runtime == null) {
            throw new IllegalStateException("Mob instance is no longer active: " + handle);
        }

        if (!owned(owner, runtime)) {
            throw new IllegalArgumentException("Mob instance is owned by another plugin: " + handle);
        }

        return runtime;
    }

    private boolean owned(final ServiceOwner owner, final RuntimeMob runtime) {
        return runtime.ownerName.equals(VexMobRegistryCoordinatorService.ownerName(owner));
    }

    private RuntimeMob findRuntime(final Entity entity) {
        UUID instanceId = byEntity.get(entity.getUniqueId());

        return instanceId == null ? null : mobs.get(instanceId);
    }

    private MobSnapshot snapshot(final RuntimeMob runtime) {
        return new MobSnapshot(
            runtime.handle,
            runtime.entity.getUniqueId(),
            runtime.scope,
            runtime.entity.getLocation(),
            runtime.health,
            runtime.definition.maxHealth(),
            runtime.scale,
            runtime.definition.movementSpeed(),
            runtime.definition.rotationSpeed(),
            Optional.ofNullable(runtime.glow)
        );
    }

    private static void setAttribute(final LivingEntity entity, final Attribute attribute, final double value) {
        AttributeInstance instance = entity.getAttribute(attribute);

        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static final class RuntimeMob {

        private AttackCharge attackCharge;
        private Entity deathAttacker;
        private final String ownerName;
        private final MobHandle handle;
        private final MobDefinition definition;
        private final MobScope scope;
        private final Mob entity;
        private final Map<UUID, HologramSession> holograms = new ConcurrentHashMap<>();
        private final MobPresentationGate presentationGate = new MobPresentationGate();
        private double health;
        private double scale;
        private DisplayGlowColor glow;
        private boolean removing;

        private RuntimeMob(
            final String ownerName,
            final MobHandle handle,
            final MobDefinition definition,
            final MobScope scope,
            final Mob entity,
            final double health,
            final double scale,
            final DisplayGlowColor glow
        ) {
            this.ownerName = ownerName;
            this.handle = handle;
            this.definition = definition;
            this.scope = scope;
            this.entity = entity;
            this.health = health;
            this.scale = scale;
            this.glow = glow;
        }
    }

    private record HologramSession(FakeDisplayHandle handle, long epoch) {

    }

    private record AttackCharge(UUID player, int tick, double value) {

    }
}
