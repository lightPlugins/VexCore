package dev.vexsoft.core.paper.service.profile;

import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDataSnapshot;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.ProfileMutation;
import dev.vexsoft.core.api.player.profile.ProfileSelection;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.network.AdmissionService;
import dev.vexsoft.core.api.service.player.profile.ProfileLeaseService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TeleportStatus;
import dev.vexsoft.core.common.data.redis.IndeterminateCommitException;
import dev.vexsoft.core.common.messaging.teleport.TeleportCompletion;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import dev.vexsoft.core.common.service.data.PlayerTransferStoreService;
import dev.vexsoft.core.common.service.data.PlayerTransferTicket;
import dev.vexsoft.core.common.service.data.ScopedPlayerDataKeys;
import dev.vexsoft.core.paper.profile.PaperProfileParticipant;
import dev.vexsoft.core.paper.profile.PaperProfileState;
import dev.vexsoft.core.paper.profile.ProfileItemInbox;
import dev.vexsoft.core.paper.profile.ProfileItemRealm;
import dev.vexsoft.core.paper.service.network.ServerIdentityService;
import dev.vexsoft.core.paper.service.teleport.TransferDisplayService;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Runs profile transitions through fenced persistence and entity-thread native state restoration. */
@Dependencies({PlayerDataCoordinatorService.class, ProfileLeaseService.class, ProfileStorageService.class,
    PlayerDataStoreService.class, PlayerTransferStoreService.class, ServerIdentityService.class, AdmissionService.class, TransferDisplayService.class})
public final class VexProfileSessionService implements ProfileSessionService, AutoCloseable {

    private final PlayerDataCoordinatorService players;
    private final ProfileLeaseService leases;
    private final ProfileStorageService storage;
    private final Plugin plugin;
    private final VexServiceRegistry services;
    private final PlayerDataStoreService backend;
    private final PlayerTransferStoreService transfers;
    private final String serverId;
    private final AdmissionService admissions;
    private final TransferDisplayService transferDisplays;
    private final Set<VexPlayer> suspended = ConcurrentHashMap.newKeySet();
    private final AutoCloseable retirementRegistration;
    private final AutoCloseable captureRegistration;
    private final Map<VexPlayer, Session> sessions = new ConcurrentHashMap<>();
    private final ScopedPlayerDataKeys<PaperProfileState> nativeKeys = new ScopedPlayerDataKeys<>(
        DataContainerKey.of("native_state", PaperProfileState.class, PaperProfileState::empty), PaperProfileState::empty);
    private final ScopedPlayerDataKeys<ProfileItemInbox> inboxKeys = new ScopedPlayerDataKeys<>(
        DataContainerKey.of("native_inbox", ProfileItemInbox.class, ProfileItemInbox::empty), ProfileItemInbox::empty);
    private volatile DomainBinding binding;

    public VexProfileSessionService(final VexServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
        players = services.require(PlayerDataCoordinatorService.class);
        leases = services.require(ProfileLeaseService.class);
        storage = services.require(ProfileStorageService.class);
        backend = services.require(PlayerDataStoreService.class);
        transfers = services.require(PlayerTransferStoreService.class);
        admissions = services.require(AdmissionService.class);
        transferDisplays = services.require(TransferDisplayService.class);
        serverId = services.require(ServerIdentityService.class).getServerId().value();
        if (!(services.getOwner() instanceof Plugin owner)) {
            throw new IllegalArgumentException("Profile platform service requires a Paper plugin owner");
        }
        plugin = owner;
        captureRegistration = players.onCapture((player, expected) -> {
            if (expected == null) {
                return save(player);
            }
            requireBinding(expected.domain());
            return save(player, expected);
        });
        retirementRegistration = leases.onRetirement(context -> {
            VexPlayer player = players.find(context.playerId()).orElse(null);
            if (player == null || !player.isCurrentContext(context)) {
                return leases.release(context);
            }
            Player platform = player.findPlatformPlayer(Player.class).orElse(null);
            return retire(player).thenCompose(ignored -> platform == null ? CompletableFuture.completedFuture(null)
                : entity(platform, () -> {
                    platform.kick(Component.text(backend.getLoginKickMessage()));
                    return null;
                }));
        });
    }

    @Override
    public synchronized AutoCloseable register(final PaperProfileParticipant participant) {
        Objects.requireNonNull(participant, "participant");
        if (binding != null) {
            throw new IllegalStateException("Native player state already participates in a profile domain");
        }
        DataContainerKey<PaperProfileState> key = nativeKeys.key(PlayerDataScope.profile(participant.domain()));
        DataContainerKey<ProfileItemInbox> inbox = inboxKeys.key(PlayerDataScope.profile(participant.domain()));
        players.register(services.getOwner(), registry -> {
            registry.register(key);
            registry.register(inbox);
        });
        DomainBinding installed = new DomainBinding(participant, key, inbox);
        binding = installed;
        return () -> {
            synchronized (this) {
                if (binding == installed) {
                    CompletableFuture.allOf(sessions.keySet().stream()
                        .map(this::retire).toArray(CompletableFuture[]::new)).thenRun(() -> {
                            synchronized (this) {
                                if (binding == installed) {
                                    binding = null;
                                }
                            }
                        });
                }
            }
        };
    }

    @Override
    public Optional<ProfileDomain> domain() {
        return Optional.ofNullable(binding).map(current -> current.participant().domain());
    }

    @Override
    public boolean acceptingTransfers() {
        DomainBinding current = binding;
        return current == null || current.participant().acceptingTransfers();
    }

    @Override
    public CompletableFuture<Void> initialize(final VexPlayer player) {
        DomainBinding current = binding;
        if (current == null) {
            suspended.add(player);
            return transfers.claim(player.getUniqueId(), serverId, UUID.randomUUID()).thenCompose(ticket -> {
                if (ticket.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                PlayerTransferTicket arrival = ticket.get();
                transferDisplays.show(player, arrival.requestId(), arrival.options().presentation());
                CompletableFuture<Void> restored = validateArrival(arrival).thenCompose(ignored -> {
                    if (!arrival.profiles().isEmpty() && !arrival.options().accountOnly()) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                            "The destination has no participant for the transferred profiles"));
                    } else if (arrival.destination() == null) {
                        return CompletableFuture.completedFuture(null);
                    } else {
                        Player platform = player.requirePlatformPlayer(Player.class);
                        var destination = arrival.destination();
                        return entity(platform, () -> {
                            var key = destination.world();
                            var world = Bukkit.getWorld(new NamespacedKey(key.namespace(), key.value()));
                            if (world == null) {
                                throw new IllegalStateException("Arrival world is unavailable");
                            }
                            return platform.teleportAsync(new Location(world, destination.x(), destination.y(),
                                destination.z(), destination.yaw(), destination.pitch()));
                        }).thenCompose(teleported -> teleported).thenAccept(teleported -> {
                            if (!teleported) {
                                throw new IllegalStateException("Arrival teleport was rejected");
                            }
                        }).orTimeout(30, TimeUnit.SECONDS);
                    }
                });
                return finishArrival(player, arrival, restored);
            }).thenRun(() -> suspended.remove(player));
        }
        Session session = sessions.computeIfAbsent(player, ignored -> new Session());
        synchronized (session) {
            if (session.started) {
                return session.ready;
            }
            session.started = true;
            CompletableFuture<Void> initialized = transfers.claim(player.getUniqueId(), serverId, session.id)
                .thenCompose(ticket -> {
                    session.arrival = ticket.orElse(null);
                    if (session.arrival != null) {
                        transferDisplays.show(player, session.arrival.requestId(), session.arrival.options().presentation());
                    }
                    CompletableFuture<Void> admitted = session.arrival == null
                        ? CompletableFuture.completedFuture(null) : validateArrival(session.arrival);
                    if (session.arrival != null && session.arrival.options().accountOnly()) {
                        admitted = CompletableFuture.failedFuture(
                            new IllegalStateException("Account-only arrival reached a game backend"));
                    }
                    CompletableFuture<Void> restored = admitted.thenCompose(ignored -> current.participant().bootstrap(player))
                        .thenCompose(selection -> {
                        if (session.arrival != null && !session.arrival.profiles().isEmpty()
                            && session.arrival.profiles().stream().noneMatch(expected ->
                            expected.domain().equals(selection.domain()) && expected.profileId().equals(selection.profileId())
                                && expected.membershipEpoch() == selection.membershipEpoch())) {
                            return CompletableFuture.failedFuture(new IllegalStateException("Transferred profile changed"));
                        }
                        return activate(player, selection);
                    });
                    return session.arrival == null ? restored : finishArrival(player, session.arrival, restored);
                });
            return initialized.whenComplete((ignored, failure) -> {
                session.arrival = null;
                if (failure != null) {
                    session.ready.completeExceptionally(failure);
                } else {
                    session.ready.complete(null);
                }
            });
        }
    }

    @Override
    public CompletableFuture<Void> activate(final VexPlayer player, final ProfileSelection selection) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(selection, "selection");
        DomainBinding current = requireBinding(selection.domain());
        Player platform = player.findPlatformPlayer(Player.class).orElse(null);
        if (platform == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player disconnected before profile switch"));
        }
        Session session = sessions.computeIfAbsent(player, ignored -> new Session());
        CompletableFuture<Void> ready;
        synchronized (session) {
            if (session.changing.get() || session.retired) {
                return CompletableFuture.failedFuture(new IllegalStateException("Profile session is changing or retired"));
            }
            session.changing.set(true);
            session.restoring = true;
            ready = new CompletableFuture<>();
            session.ready = ready;
        }
        PlayerProfileContext previous = player.findProfileContext(selection.domain()).orElse(null);
        boolean previousWritable = previous != null && leases.isWritable(previous);
        if (previous != null && previous.profileId().equals(selection.profileId()) && leases.isWritable(previous)) {
            session.restoring = false;
            session.changing.set(false);
            ready.complete(null);
            return CompletableFuture.failedFuture(new IllegalArgumentException("Profile already active"));
        }
        Transition transition = new Transition();
        CompletableFuture<Void> result = entity(platform, () -> {
            if (platform.isDead()) {
                throw new IllegalStateException("Cannot switch profiles while dead");
            }
            platform.closeInventory();
            current.participant().prepare(platform, previousWritable ? previous : null);
            if (previousWritable) {
                capture(player, platform, current);
            }
            return null;
        }).thenCompose(ignored -> players.save(player.getUniqueId()))
            .thenCompose(ignored -> acquire(player, selection, session, platform, ++session.generation,
                previous == null ? System.nanoTime() + TimeUnit.SECONDS.toNanos(50) : 0))
            .thenCompose(acquired -> {
                transition.target = acquired.orElseThrow(() -> new IllegalStateException("Profile is in use"));
                return players.loadProfile(transition.target);
            }).thenCompose(snapshot -> {
                if (session.retired || player.findPlatformPlayer(Player.class).orElse(null) != platform) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Player disconnected during switch"));
                }
                var changes = new ArrayList<>(selection.changes());
                changes.add(leases.writeGuard(transition.target));
                changes.addAll(selection.writeGuards());
                return storage.commit(changes).thenCompose(committed -> {
                    if (!committed) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Profile selection changed"));
                    }
                    transition.committed = true;
                    return entity(platform, () -> {
                        if (transition.failed || session.retired || !platform.isConnected()) {
                            throw new IllegalStateException("Player disconnected after profile commit");
                        }
                        players.installProfile(player, snapshot, selection.writeGuards());
                        session.writeGuards = selection.writeGuards();
                        PaperProfileState state = player.read(current.nativeKey(), value -> value);
                        PaperProfileStateCodec.apply(platform, state);
                        return session.arrival != null && session.arrival.destination() != null
                            ? current.participant().arrivalDestination(platform, session.arrival.destination())
                            : current.participant().destination(platform, state, previous == null);
                    }).thenCompose(destination -> destination)
                        .thenCompose(location -> entity(platform, () -> {
                            if (transition.failed || session.retired || !player.isCurrentContext(transition.target)
                                || !leases.isWritable(transition.target)) {
                                throw new IllegalStateException("Profile restoration retired before teleport");
                            }
                            return platform.teleportAsync(location);
                        })
                            .thenCompose(teleport -> teleport))
                        .orTimeout(current.participant().restoreTimeoutSeconds(), TimeUnit.SECONDS)
                        .thenCompose(teleported -> {
                            if (!teleported) {
                                return CompletableFuture.failedFuture(new IllegalStateException("Activation teleport failed"));
                            }
                            return entity(platform, () -> {
                                if (transition.failed || session.retired || !player.isCurrentContext(transition.target)) {
                                    throw new IllegalStateException("Profile restoration retired before gameplay");
                                }
                                current.participant().restore(platform, transition.target);
                                return null;
                            });
                        });
                });
            }).thenCompose(ignored -> previous == null ? CompletableFuture.completedFuture(null)
                : leases.release(previous));
        CompletableFuture<Void> settled = result.exceptionallyCompose(failure -> {
            transition.failed = true;
            if (transition.committed || indeterminate(failure)) {
                // A partially restored target must never overwrite its durable source during quit or shutdown.
                session.poisoned = true;
            }
            CompletableFuture<Void> recovery;
            if (!session.poisoned && !transition.committed && previousWritable
                && player.findPlatformPlayer(Player.class).orElse(null) == platform && !session.retired) {
                recovery = entity(platform, () -> {
                    current.participant().restore(platform, previous);
                    transition.recovered = true;
                    return null;
                });
            } else {
                recovery = entity(platform, () -> {
                    platform.kick(Component.text(backend.getLoginKickMessage()));
                    return null;
                });
            }
            return recovery.handle((ignored, recoveryFailure) -> null)
                .thenCompose(ignored -> transition.target == null ? CompletableFuture.completedFuture(null)
                    : leases.release(transition.target).handle((released, releaseFailure) -> null))
                .thenCompose(ignored -> !transition.committed || previous == null
                    ? CompletableFuture.completedFuture(null)
                    : leases.release(previous).handle((released, releaseFailure) -> null))
                .thenCompose(ignored -> CompletableFuture.failedFuture(failure));
        });
        return settled.whenComplete((ignored, failure) -> {
            boolean restored = failure == null || transition.recovered;
            if (failure == null) {
                backend.getStore().requestCheckpoint();
            }
            session.restoring = !restored;
            session.changing.set(false);
            if (restored) {
                if (session.arrival == null) {
                    ready.complete(null);
                }
            } else {
                ready.completeExceptionally(failure);
            }
        });
    }

    @Override
    public CompletableFuture<Void> save(final VexPlayer player) {
        return save(player, null);
    }

    @Override
    public Optional<PlayerProfileContext> activeContext(final VexPlayer player) {
        DomainBinding current = binding;
        return current == null ? Optional.empty() : player.findProfileContext(current.participant().domain());
    }

    @Override
    public boolean persistItem(final VexPlayer player, final ItemStack item) {
        DomainBinding current = binding;
        return current == null || current.participant().persistItem(item);
    }

    private CompletableFuture<Void> save(final VexPlayer player, final PlayerProfileContext expected) {
        DomainBinding current = binding;
        Session session = sessions.get(player);
        if (current == null || session == null) {
            return players.save(player.getUniqueId());
        }
        Player platform = player.findPlatformPlayer(Player.class).orElse(null);
        if (platform == null) {
            return expected == null ? players.save(player.getUniqueId()) : players.save(expected);
        }
        if (backend.getStore().isLive()) {
            return saveLive(player, platform, current, session, expected);
        }
        CompletableFuture<Void> ready;
        synchronized (session) {
            if (session.retired) {
                return CompletableFuture.completedFuture(null);
            }
            if (session.changing.get() || !session.ready.isDone()) {
                return session.ready.thenCompose(ignored -> save(player, expected));
            }
            if (!isReady(player) || expected != null && !player.isCurrentContext(expected)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Profile session is not writable"));
            }
            session.changing.set(true);
            ready = new CompletableFuture<>();
            session.ready = ready;
        }
        CompletableFuture<Void> saved = entity(platform, () -> {
            if (session.retired) {
                return null;
            }
            if (expected != null && !player.isCurrentContext(expected)) {
                throw new IllegalStateException("Captured save context retired");
            }
            capture(player, platform, current);
            return null;
        }, () -> null).thenCompose(ignored -> expected == null
            ? players.save(player.getUniqueId()) : players.save(expected));
        return saved.whenComplete((ignored, failure) -> {
            session.changing.set(false);
            if (failure != null && indeterminate(failure)) {
                poison(session, platform);
                ready.completeExceptionally(failure);
            } else {
                // Confirmed failures retain dirty data and permit a retry from the same live session.
                ready.complete(null);
            }
        });
    }

    private CompletableFuture<Void> saveLive(
        final VexPlayer player,
        final Player platform,
        final DomainBinding current,
        final Session session,
        final PlayerProfileContext expected
    ) {
        CompletableFuture<Void> saved;
        synchronized (session) {
            if (session.retired) {
                return CompletableFuture.completedFuture(null);
            }
            if (session.changing.get() || !session.ready.isDone()) {
                return session.ready.thenCompose(ignored -> save(player, expected));
            }
            if (!isReady(player) || expected != null && !player.isCurrentContext(expected)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Profile session is not writable"));
            }
            if (!session.backgroundSave.isDone()) {
                return session.backgroundSave;
            }
            saved = new CompletableFuture<>();
            session.backgroundSave = saved;
        }
        entity(platform, () -> {
            // A switch or transaction captures its own baseline; do not capture halfway through that operation.
            if (session.changing.get() || session.retired) {
                return false;
            }
            if (expected != null && !player.isCurrentContext(expected)) {
                throw new IllegalStateException("Captured save context retired");
            }
            synchronized (player) {
                capture(player, platform, current);
            }
            return true;
        }, () -> false).thenCompose(captured -> captured ? players.save(player.getUniqueId())
            : CompletableFuture.completedFuture(null)).whenComplete((ignored, failure) -> {
                if (failure == null) {
                    saved.complete(null);
                } else {
                    if (indeterminate(failure)) {
                        poison(session, platform);
                    }
                    saved.completeExceptionally(failure);
                }
            });
        return saved;
    }

    @Override
    public <T> CompletableFuture<T> transact(final VexPlayer player, final Supplier<ProfileMutation<T>> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        DomainBinding current = binding;
        Session session = sessions.get(player);
        if (current == null || session == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Profile session is not ready"));
        }
        Player platform = player.findPlatformPlayer(Player.class).orElse(null);
        if (platform == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player disconnected before profile operation"));
        }
        CompletableFuture<Void> ready;
        synchronized (session) {
            if (!session.retired && !session.poisoned && (session.changing.get() || !session.ready.isDone())) {
                return CompletableFuture.failedFuture(new ProfileSessionBusyException());
            }
            if (!isReady(player)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Profile session is not ready"));
            }
            session.changing.set(true);
            ready = new CompletableFuture<>();
            session.ready = ready;
        }
        PlayerProfileContext context = player.findProfileContext(current.participant().domain()).orElseThrow();
        DurableOperation<T> operation = new DurableOperation<>();
        boolean live = backend.getStore().isLive();
        CompletableFuture<Void> baseline = live ? session.backgroundSave.handle((ignored, failure) -> {
            if (failure != null && indeterminate(failure)) {
                throw new IllegalStateException("Live save outcome is unknown", failure);
            }
            return null;
        }) : CompletableFuture.completedFuture(null);
        CompletableFuture<T> result = baseline.thenCompose(ignored -> entity(platform, () -> {
            capture(player, platform, current);
            return null;
        })).thenCompose(ignored -> live ? CompletableFuture.completedFuture(null)
            : players.save(player.getUniqueId()))
            .thenCompose(ignored -> players.snapshotProfile(player, context))
            .thenCompose(before -> {
                operation.before = before;
                return entity(platform, () -> {
                    if (session.retired || !player.isCurrentContext(context) || !leases.isWritable(context)) {
                        throw new IllegalStateException("Captured economic session retired");
                    }
                    // Background snapshots must see wallet and native inventory before or after the whole mutation.
                    synchronized (player) {
                        player.beginDurableEffects();
                        operation.started = true;
                        operation.mutation = Objects.requireNonNull(player.inProfileMutation(context.domain(), mutation),
                            "mutation result");
                        capture(player, platform, current);
                    }
                    return operation.mutation;
                });
            }).thenCompose(prepared -> players.save(player.getUniqueId(), prepared.changes()))
            .thenCompose(ignored -> {
                operation.committed = true;
                // Durability defines success even when the player disconnects before a notification can run.
                return entity(platform, () -> {
                    player.finishDurableEffects(true);
                    return operation.mutation.result();
                }, () -> {
                    // Quit can retire the notification task after the purchase has already committed.
                    player.finishDurableEffects(false);
                    return operation.mutation.result();
                }).handle((value, notificationFailure) -> {
                    if (notificationFailure != null) {
                        player.finishDurableEffects(false);
                        plugin.getLogger().log(Level.WARNING,
                            "Profile operation committed but its notification could not complete", notificationFailure);
                    }
                    return operation.mutation.result();
                });
            });
        CompletableFuture<T> settled = result.exceptionallyCompose(failure -> {
            if (!operation.committed && indeterminate(failure)) {
                // Even a baseline capture can have committed. Never compensate an unknown Redis outcome.
                if (operation.started) {
                    player.finishDurableEffects(false);
                }
                poison(session, platform);
                return CompletableFuture.failedFuture(failure);
            }
            if (!operation.started || operation.committed) {
                return CompletableFuture.failedFuture(failure);
            }
            player.finishDurableEffects(false);
            CompletableFuture<Void> recovery = entity(platform, () -> {
                players.installProfile(player, operation.before, session.writeGuards);
                if (live) {
                    // The local baseline may contain gameplay changes not yet acknowledged by Redis.
                    operation.before.values().forEach((key, value) -> restoreDirty(player, key, value));
                }
                PaperProfileStateCodec.apply(platform, player.read(current.nativeKey(), value -> value));
                current.participant().restore(platform, context);
                return null;
            });
            return recovery.handle((ignored, recoveryFailure) -> {
                if (recoveryFailure != null && player.findPlatformPlayer(Player.class).isEmpty()) {
                    player.rollbackRetiredProfile(context, operation.before.values());
                    if (live) {
                        operation.before.values().forEach((key, value) -> restoreDirty(player, key, value));
                    }
                } else if (recoveryFailure != null) {
                    session.poisoned = true;
                    entity(platform, () -> {
                        platform.kick(Component.text(backend.getLoginKickMessage()));
                        return null;
                    });
                    throw new IllegalStateException("Economic rollback failed", recoveryFailure);
                }
                return null;
            }).thenCompose(ignored -> CompletableFuture.failedFuture(failure));
        });
        return settled.whenComplete((value, failure) -> {
            session.changing.set(false);
            if (session.poisoned) {
                ready.completeExceptionally(new IllegalStateException("Profile rollback requires reconnect"));
            } else {
                ready.complete(null);
            }
        });
    }

    private static boolean indeterminate(final Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IndeterminateCommitException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static <T> void restoreDirty(final VexPlayer player, final DataContainerKey<T> key, final Object value) {
        player.replace(key, key.getType().cast(value));
    }

    private void poison(final Session session, final Player platform) {
        session.poisoned = true;
        entity(platform, () -> {
            platform.kick(Component.text(backend.getLoginKickMessage()));
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> retire(final VexPlayer player) {
        DomainBinding current = binding;
        Session session = sessions.get(player);
        if (session == null || current == null) {
            suspended.add(player);
            return players.save(player.getUniqueId());
        }
        synchronized (session) {
            if (session.retired && !session.retirement.isCompletedExceptionally()) {
                return session.retirement;
            }
            session.retired = true;
            PlayerProfileContext context = player.findProfileContext(current.participant().domain()).orElse(null);
            Player platform = player.findPlatformPlayer(Player.class).orElse(null);
            CompletableFuture<Void> captured = platform == null || context == null || !leases.isWritable(context)
                || session.changing.get() || session.poisoned
                ? CompletableFuture.completedFuture(null) : entity(platform, () -> {
                    platform.closeInventory();
                    current.participant().prepare(platform, context);
                    capture(player, platform, current);
                    return null;
                }, () -> null);
            session.retirement = captured.thenCompose(ignored -> session.ready.handle((ready, failure) -> null))
                .thenCompose(ignored -> session.backgroundSave.handle((saved, failure) -> null))
                .thenCompose(ignored -> session.poisoned || platform == null || context == null || !leases.isWritable(context)
                    || player.findPlatformPlayer(Player.class).orElse(null) != platform
                    ? CompletableFuture.completedFuture(null) : entity(platform, () -> {
                        // A busy purchase or older autosave can finish after the first capture. Drain before the final one.
                        current.participant().prepare(platform, context);
                        capture(player, platform, current);
                        return null;
                    }, () -> null))
                .thenCompose(ignored -> session.poisoned ? CompletableFuture.completedFuture(null)
                    : players.save(player.getUniqueId()))
                .thenCompose(ignored -> releaseContexts(player))
                .whenComplete((ignored, failure) -> {
                    if (failure == null) {
                        sessions.remove(player, session);
                        backend.getStore().requestCheckpoint();
                    }
                });
            return session.retirement;
        }
    }

    @Override
    public CompletableFuture<Void> readiness(final VexPlayer player) {
        Session session = sessions.get(player);
        return session == null ? CompletableFuture.completedFuture(null) : session.ready;
    }

    @Override
    public CompletableFuture<Void> resume(final VexPlayer player) {
        Session previous = sessions.get(player);
        CompletableFuture<Void> drained = previous == null ? CompletableFuture.completedFuture(null) : retire(player);
        return drained.thenCompose(ignored -> {
            sessions.remove(player);
            return initialize(player).thenRun(() -> suspended.remove(player));
        });
    }

    @Override
    public boolean isReady(final VexPlayer player) {
        if (suspended.contains(player)) {
            return false;
        }
        DomainBinding current = binding;
        Session session = sessions.get(player);
        if (current == null) {
            return player.findPlatformPlayer(Player.class).isPresent();
        }
        return session != null && player.findPlatformPlayer(Player.class).isPresent()
            && !session.retired && !session.poisoned && !session.changing.get() && session.ready.isDone()
            && !session.ready.isCompletedExceptionally()
            && player.findProfileContext(current.participant().domain()).map(leases::isWritable).orElse(false);
    }

    @Override
    public boolean isMovementAllowed(final VexPlayer player) {
        if (suspended.contains(player)) {
            return false;
        }
        DomainBinding current = binding;
        Session session = sessions.get(player);
        if (current == null) {
            return player.findPlatformPlayer(Player.class).isPresent();
        }
        // Saving and economic transactions guard inventory state without freezing the player's position.
        return session != null && player.findPlatformPlayer(Player.class).isPresent()
            && !session.retired && !session.restoring && !session.poisoned
            && player.findProfileContext(current.participant().domain()).map(leases::isWritable).orElse(false);
    }

    @Override
    public boolean deferDelivery(final VexPlayer player, final List<ItemStack> items) {
        DomainBinding current = binding;
        if (current == null) {
            return false;
        }
        synchronized (player) {
            var queued = new ArrayList<>(player.read(current.inboxKey(), ProfileItemInbox::items));
            var identities = new ArrayList<>(player.read(current.inboxKey(), ProfileItemInbox::possessionIdentities));
            items.forEach(item -> {
                if (!ProfileItemRealm.permits(item,
                    player.findProfileContext(current.participant().domain()).orElseThrow())) {
                    throw new IllegalStateException("Queued item belongs to another profile");
                }
                queued.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                current.participant().possessionIdentity(item).ifPresent(identities::add);
            });
            player.replace(current.inboxKey(), new ProfileItemInbox(queued, identities));
        }
        return true;
    }

    @Override
    public CompletableFuture<Void> collectDeliveries(final VexPlayer player) {
        DomainBinding current = binding;
        if (current == null) {
            return CompletableFuture.completedFuture(null);
        }
        return transact(player, () -> {
            Player platform = player.requirePlatformPlayer(Player.class);
            var inbox = player.read(current.inboxKey(), value -> value);
            var remaining = new ArrayList<String>();
            var identities = new ArrayList<>(inbox.possessionIdentities());
            // Bound native NBT conversion per request. Undelivered bytes and their ownership index stay durable.
            int processed = 0;
            for (String serialized : inbox.items()) {
                if (processed++ >= 64) {
                    remaining.add(serialized);
                    continue;
                }
                ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(serialized));
                current.participant().possessionIdentity(stack).ifPresent(identities::remove);
                platform.getInventory().addItem(stack).values().forEach(leftover -> {
                    remaining.add(Base64.getEncoder().encodeToString(leftover.serializeAsBytes()));
                    current.participant().possessionIdentity(leftover).ifPresent(identities::add);
                });
            }
            player.replace(current.inboxKey(), new ProfileItemInbox(remaining, identities));
            return ProfileMutation.<Void>of(null);
        });
    }

    @Override
    public void close() {
        sessions.keySet().forEach(this::retire);
        try {
            retirementRegistration.close();
            captureRegistration.close();
        } catch (Exception failure) {
            plugin.getLogger().warning("Unable to close profile retirement registration: " + failure.getMessage());
        }
    }

    @Override
    public void disconnected(final VexPlayer player) {
        suspended.remove(player);
        transferDisplays.disconnected(player);
    }

    private CompletableFuture<Optional<PlayerProfileContext>> acquire(
        final VexPlayer player, final ProfileSelection selection, final Session session, final Player platform,
        final long generation, final long deadline
    ) {
        if (session.retired || player.findPlatformPlayer(Player.class).orElse(null) != platform) {
            return CompletableFuture.failedFuture(new IllegalStateException("Login retired while waiting for profile ownership"));
        }
        return leases.acquire(selection.domain(), selection.profileId(), player.getUniqueId(),
            session.id, generation, selection.membershipEpoch()).thenCompose(acquired -> {
                if (acquired.isPresent() || deadline == 0 || System.nanoTime() >= deadline) {
                    return CompletableFuture.completedFuture(acquired);
                }
                // A crashed or rapidly reconnecting source can still hold its lease. Never sleep on an entity thread.
                return CompletableFuture.supplyAsync(() -> null, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS))
                    .thenCompose(ignored -> acquire(player, selection, session, platform, generation, deadline));
            });
    }

    private CompletableFuture<Void> finishArrival(
        final VexPlayer player, final PlayerTransferTicket ticket, final CompletableFuture<Void> restored
    ) {
        return restored.handle((ignored, failure) -> failure).thenCompose(failure -> transfers.finish(ticket)
            .thenCompose(ignored -> ticket.options().admission() == null ? CompletableFuture.completedFuture(null)
                : admissions.finish(ticket.options().admission(), failure == null))
            .thenCompose(ignored -> {
                transferDisplays.finish(player, ticket.requestId(), failure == null);
                services.require(MessagingService.class).send(MessageTarget.server(ticket.sourceServer()),
                    TeleportMessages.COMPLETION, new TeleportCompletion(ticket.requestId(),
                        failure == null ? TeleportStatus.SUCCESS : TeleportStatus.FAILED,
                        failure == null ? "" : "The destination could not restore the transferred profile"));
                return failure == null ? CompletableFuture.completedFuture(null) : CompletableFuture.failedFuture(failure);
            }));
    }

    private CompletableFuture<Void> validateArrival(final PlayerTransferTicket ticket) {
        var reference = ticket.options().admission();
        if (reference == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!reference.playerId().equals(ticket.playerId()) || !reference.server().value().equals(serverId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Arrival admission identity mismatch"));
        }
        return admissions.validate(reference).thenCompose(valid -> valid ? CompletableFuture.completedFuture(null)
            : CompletableFuture.failedFuture(new IllegalStateException("Arrival admission expired")));
    }

    private CompletableFuture<Void> releaseContexts(final VexPlayer player) {
        return CompletableFuture.allOf(player.profileContexts().values().stream()
            .map(leases::release).toArray(CompletableFuture[]::new));
    }

    private void capture(final VexPlayer player, final Player platform, final DomainBinding current) {
        PaperProfileStateCodec.validateRealm(platform,
            player.findProfileContext(current.participant().domain()).orElseThrow());
        PaperProfileState captured = PaperProfileStateCodec.capture(platform, current.participant());
        var identities = new ArrayList<>(captured.possessionIdentities());
        identities.addAll(player.read(current.inboxKey(), ProfileItemInbox::possessionIdentities));
        current.participant().validatePossessions(player, identities);
        if (!backend.getStore().isLive() || !captured.sameState(player.read(current.nativeKey(), value -> value))) {
            player.replace(current.nativeKey(), captured);
        }
    }

    private DomainBinding requireBinding(final ProfileDomain domain) {
        DomainBinding current = binding;
        if (current == null || !current.participant().domain().equals(domain)) {
            throw new IllegalArgumentException("Profile domain is not registered");
        }
        return current;
    }

    private <T> CompletableFuture<T> entity(final Player player, final Supplier<T> operation) {
        return entity(player, operation, () -> {
            throw new IllegalStateException("Player entity retired");
        });
    }

    private <T> CompletableFuture<T> entity(
        final Player player,
        final Supplier<T> operation,
        final Supplier<T> retiredOperation
    ) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable action = () -> completeEntityOperation(result, operation);
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            action.run();
        } else {
            // A scheduler already retired rejects the task without invoking either callback.
            Runnable retired = () -> completeEntityOperation(result, retiredOperation);
            if (player.getScheduler().run(plugin, task -> action.run(), retired) == null) {
                retired.run();
            }
        }
        return result;
    }

    private static <T> void completeEntityOperation(final CompletableFuture<T> result, final Supplier<T> operation) {
        try {
            result.complete(operation.get());
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
    }

    private record DomainBinding(
        PaperProfileParticipant participant, DataContainerKey<PaperProfileState> nativeKey,
        DataContainerKey<ProfileItemInbox> inboxKey
    ) {
    }

    private static final class Transition {

        private PlayerProfileContext target;
        private boolean committed;
        private boolean recovered;
        private volatile boolean failed;
    }

    private static final class Session {
        private PlayerTransferTicket arrival;

        private final UUID id = UUID.randomUUID();
        private final AtomicBoolean changing = new AtomicBoolean();
        private volatile CompletableFuture<Void> ready = new CompletableFuture<>();
        private volatile CompletableFuture<Void> backgroundSave = CompletableFuture.completedFuture(null);
        private CompletableFuture<Void> retirement = CompletableFuture.completedFuture(null);
        private volatile boolean started;
        private volatile boolean restoring = true;
        private volatile boolean retired;
        private volatile boolean poisoned;
        private long generation;
        private List<ProfileDocumentChange> writeGuards = List.of();
    }

    private static final class DurableOperation<T> {

        private ProfileDataSnapshot before;
        private ProfileMutation<T> mutation;
        private boolean started;
        private boolean committed;
    }
}
