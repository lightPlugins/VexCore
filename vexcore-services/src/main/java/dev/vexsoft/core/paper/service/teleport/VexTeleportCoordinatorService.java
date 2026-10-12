package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.network.AdmissionService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TeleportResult;
import dev.vexsoft.core.api.teleport.TeleportStatus;
import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.common.messaging.teleport.TeleportAbort;
import dev.vexsoft.core.common.messaging.teleport.TeleportCompletion;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparation;
import dev.vexsoft.core.common.messaging.teleport.TeleportTransferRequest;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.data.PlayerTransferStoreService;
import dev.vexsoft.core.common.service.data.PlayerTransferTicket;
import dev.vexsoft.core.paper.service.network.ServerIdentityService;
import dev.vexsoft.core.paper.service.profile.ProfileSessionService;
import dev.vexsoft.core.paper.service.world.WorldService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Default async Paper teleport and Velocity transfer coordinator. */
@Dependencies({WorldService.class, ServerIdentityService.class, MessagingService.class,
    PlayerDataCoordinatorService.class, ProfileSessionService.class, PlayerTransferStoreService.class,
    ProfileStorageService.class, AdmissionService.class})
public final class VexTeleportCoordinatorService implements TeleportCoordinatorService, AutoCloseable {

    private static final long TRANSFER_TIMEOUT_SECONDS = 90L;

    private final Plugin plugin;
    private final WorldService worlds;
    private final ServerIdentityService serverIdentity;
    private final MessagingService messages;
    private final PlayerDataCoordinatorService playerData;
    private final ProfileSessionService profiles;
    private final PlayerTransferStoreService transfers;
    private final ProfileStorageService storage;
    private final AdmissionService admissions;
    private final ConcurrentHashMap<UUID, TransferOptions> pendingOptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<TeleportResult>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, VexPlayer> pendingPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ServerPosition> pendingDestinations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VexPlayer, UUID> outbound = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Preparation> preparations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VexPlayer, UUID> preparing = new ConcurrentHashMap<>();

    public VexTeleportCoordinatorService(final VexServiceRegistry services) {
        VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");

        if (!(checkedServices.getOwner() instanceof Plugin javaPlugin)) {
            throw new IllegalArgumentException("Teleport coordinator owner must be a Paper plugin");
        }

        plugin = javaPlugin;
        worlds = checkedServices.require(WorldService.class);
        serverIdentity = checkedServices.require(ServerIdentityService.class);
        messages = checkedServices.require(MessagingService.class);
        playerData = checkedServices.require(PlayerDataCoordinatorService.class);
        profiles = checkedServices.require(ProfileSessionService.class);
        transfers = checkedServices.require(PlayerTransferStoreService.class);
        storage = checkedServices.require(ProfileStorageService.class);
        admissions = checkedServices.require(AdmissionService.class);
    }

    @Override
    public CompletableFuture<TeleportResult> teleport(final VexPlayer player, final ServerPosition destination) {
        return teleport(player, destination, TransferOptions.ordinary());
    }

    @Override
    public CompletableFuture<TeleportResult> teleport(
        final VexPlayer player, final ServerPosition destination, final TransferOptions options
    ) {
        Objects.requireNonNull(options, "options");
        VexPlayer checkedPlayer = Objects.requireNonNull(player, "player");
        ServerPosition checkedDestination = Objects.requireNonNull(destination, "destination");

        if (checkedDestination.server().equals(serverIdentity.getServerId())) {
            if (!profiles.isMovementAllowed(checkedPlayer)) {
                return completed(TeleportStatus.TELEPORT_REJECTED, "The profile session is not ready");
            }
            return checkedPlayer.findPlatformPlayer(Player.class)
                .map(platformPlayer -> profiles.readiness(checkedPlayer)
                    .thenCompose(ignored -> teleportLocal(platformPlayer, checkedDestination)))
                .orElseGet(() -> completed(TeleportStatus.PLAYER_OFFLINE, "The player is no longer online"));
        }

        if (options.admission() != null && (!options.admission().playerId().equals(player.getUniqueId())
            || !options.admission().server().equals(destination.server()))) {
            return completed(TeleportStatus.TELEPORT_REJECTED, "Admission destination mismatch");
        }
        return (options.admission() == null ? CompletableFuture.completedFuture(true)
            : admissions.validate(options.admission())).thenCompose(valid -> valid
                ? transfer(checkedPlayer, checkedDestination, options)
                : completed(TeleportStatus.TELEPORT_REJECTED, "Admission reservation expired"));
    }

    @Override
    public CompletableFuture<TeleportResult> acceptArrival(final UUID playerId, final ServerPosition destination) {
        // Old delayed arrival messages must never teleport a newer login or overwrite its restored position.
        return completed(TeleportStatus.TELEPORT_REJECTED, "Arrivals require a claimed server handover");
    }

    @Override
    public void complete(final TeleportCompletion completion) {
        TeleportCompletion checkedCompletion = Objects.requireNonNull(completion, "completion");
        CompletableFuture<TeleportResult> future = pending.remove(checkedCompletion.requestId());

        if (future != null) {
            future.complete(new TeleportResult(checkedCompletion.status(), checkedCompletion.message()));
        }
    }

    @Override
    public void complete(final TeleportCompletion completion, final String sourceServer) {
        ServerPosition destination = pendingDestinations.get(completion.requestId());
        if (destination != null && (sourceServer.equals(destination.server().value())
            || sourceServer.isBlank() && !completion.status().equals(TeleportStatus.SUCCESS))) {
            complete(completion);
        }
    }

    @Override
    public CompletableFuture<Boolean> prepare(final TeleportPreparation request) {
        String local = serverIdentity.getServerId().value();
        VexPlayer player = playerData.find(request.playerId()).orElse(null);
        if (!local.equals(request.sourceServer()) || local.equals(request.targetServer()) || player == null
            || player.findPlatformPlayer(Player.class).isEmpty()
            || request.destination() != null && (pendingPlayers.get(request.requestId()) != player
                || !request.destination().equals(pendingDestinations.get(request.requestId()))
                || !request.options().equals(pendingOptions.get(request.requestId())))) {
            return CompletableFuture.completedFuture(false);
        }
        Preparation prior = preparations.get(request.requestId());
        if (prior != null) {
            return prior.player == player && prior.request.equals(request) ? prior.ready
                : CompletableFuture.completedFuture(false);
        }
        Preparation preparation = new Preparation(request, player);
        if (preparations.size() >= 1024) {
            return CompletableFuture.completedFuture(false);
        }
        prior = preparations.putIfAbsent(request.requestId(), preparation);
        if (prior != null) {
            return prior.player == player && prior.request.equals(request) ? prior.ready
                : CompletableFuture.completedFuture(false);
        }
        if (preparing.putIfAbsent(player, request.requestId()) != null) {
            preparations.remove(request.requestId(), preparation);
            preparation.ready.complete(false);
            return preparation.ready;
        }
        CompletableFuture<Boolean> admitted = request.options().admission() == null
            ? CompletableFuture.completedFuture(true) : admissions.validate(request.options().admission());
        CompletableFuture<Void> work = admitted.thenCompose(valid -> {
            if (!valid) {
                return CompletableFuture.failedFuture(
                    new IllegalStateException("Source admission expired before preparation"));
            }
            return profiles.readiness(player);
        }).thenCompose(ignored -> {
            if (preparation.cancelled) {
                return CompletableFuture.failedFuture(new IllegalStateException("Source handover cancelled"));
            }
            preparation.retiring = true;
            return profiles.retire(player);
        }).thenCompose(ignored -> {
            if (preparation.cancelled || playerData.find(player.getUniqueId()).orElse(null) != player
                || player.findPlatformPlayer(Player.class).isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Source handover retired"));
            }
            return storage.databaseTime().thenCompose(now -> transfers.prepare(new PlayerTransferTicket(
                request.requestId(), request.playerId(), local, request.targetServer(), request.destination(),
                List.copyOf(player.profileContexts().values()), now + 30_000, null, false, request.options())));
        });
        work.whenComplete((ignored, failure) -> {
            if (failure == null) {
                preparation.work.complete(null);
            } else {
                preparation.work.completeExceptionally(failure);
            }
        });
        preparation.work.whenComplete((ignored, failure) -> {
            if (failure == null && !preparation.cancelled) {
                preparation.published = true;
                preparation.ready.complete(true);
            } else {
                abort(new TeleportAbort(request.requestId(), request.playerId())).whenComplete((restored, problem) ->
                    preparation.ready.complete(false));
            }
        });
        CompletableFuture.runAsync(() -> abort(new TeleportAbort(request.requestId(), request.playerId())),
            CompletableFuture.delayedExecutor(45, TimeUnit.SECONDS));
        return preparation.ready;
    }

    @Override
    public CompletableFuture<Void> abort(final TeleportAbort request) {
        Preparation preparation = preparations.get(request.requestId());
        if (preparation == null || !preparation.player.getUniqueId().equals(request.playerId())) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (preparation) {
            preparation.cancelled = true;
            if (preparation.recovery != null) {
                return preparation.recovery;
            }
            preparation.recovery = preparation.work.handle((ignored, failure) -> null)
                .thenCompose(ignored -> transfers.cancel(request.playerId(), request.requestId())).thenCompose(cancelled -> {
                VexPlayer player = preparation.player;
                if (preparation.retiring && (cancelled || !preparation.published)
                    && playerData.find(request.playerId()).orElse(null) == player
                    && player.findPlatformPlayer(Player.class).isPresent()) {
                    return profiles.resume(player);
                }
                return CompletableFuture.completedFuture(null);
            }).whenComplete((ignored, failure) -> {
                preparing.remove(preparation.player, request.requestId());
                preparations.remove(request.requestId(), preparation);
                preparation.ready.complete(false);
            });
            return preparation.recovery;
        }
    }

    @Override
    public void close() {
        TeleportResult shutdown =
            new TeleportResult(TeleportStatus.FAILED, "The server stopped before the teleport completed");

        pending.values().forEach(future -> future.complete(shutdown));
        pending.clear();
        pendingPlayers.clear();
        pendingDestinations.clear();
        pendingOptions.clear();
        outbound.clear();
        preparations.values().forEach(preparation -> abort(new TeleportAbort(
            preparation.request.requestId(), preparation.player.getUniqueId())));
    }

    private CompletableFuture<TeleportResult> teleportLocal(final Player player, final ServerPosition destination) {
        CompletableFuture<TeleportResult> result = new CompletableFuture<>();

        var scheduled = player.getScheduler().run(
            plugin,
            task -> {
                if (!player.isConnected()) {
                    result.complete(new TeleportResult(
                        TeleportStatus.PLAYER_OFFLINE,
                        "The player is no longer online"
                    ));

                    return;
                }

                Location location = worlds.createLocation(destination).orElse(null);

                if (location == null) {
                    result.complete(new TeleportResult(
                        TeleportStatus.WORLD_NOT_LOADED,
                        "World " + destination.world().asString() + " is not loaded on this server"
                    ));

                    return;
                }

                player.teleportAsync(location).whenComplete((teleported, throwable) -> {
                    if (throwable != null) {
                        result.complete(new TeleportResult(TeleportStatus.FAILED, "The asynchronous teleport failed"));
                    } else if (Boolean.TRUE.equals(teleported)) {
                        result.complete(TeleportResult.success());
                    } else {
                        result.complete(new TeleportResult(
                            TeleportStatus.TELEPORT_REJECTED,
                            "The teleport was rejected by the server"
                        ));
                    }
                });
            },
            () -> result.complete(new TeleportResult(
                TeleportStatus.PLAYER_OFFLINE,
                "The player left before the teleport could start"
            ))
        );
        if (scheduled == null) {
            result.complete(new TeleportResult(
                TeleportStatus.PLAYER_OFFLINE,
                "The player left before the teleport could start"
            ));
        }

        return result;
    }

    private CompletableFuture<TeleportResult> transfer(
        final VexPlayer player, final ServerPosition destination, final TransferOptions options
    ) {
        if (!profiles.isMovementAllowed(player)) {
            return completed(TeleportStatus.TELEPORT_REJECTED, "The profile session is not ready");
        }
        return beginTransfer(player, destination, options);
    }

    private CompletableFuture<TeleportResult> beginTransfer(
        final VexPlayer player, final ServerPosition destination, final TransferOptions options
    ) {
        UUID requestId = UUID.randomUUID();
        CompletableFuture<TeleportResult> result = new CompletableFuture<>();

        if (outbound.putIfAbsent(player, requestId) != null) {
            return completed(TeleportStatus.TELEPORT_REJECTED, "A server transfer is already pending");
        }

        pending.put(requestId, result);
        pendingPlayers.put(requestId, player);
        pendingDestinations.put(requestId, destination);
        pendingOptions.put(requestId, options);
        DeliveryResult delivery;
        try {
            delivery = messages.send(
                MessageTarget.proxy(),
                TeleportMessages.TRANSFER_REQUEST,
                new TeleportTransferRequest(
                    requestId,
                    player.getUniqueId(),
                    destination,
                    serverIdentity.getServerId().value(),
                    options
                )
            );
        } catch (RuntimeException failure) {
            delivery = DeliveryResult.NO_CONNECTION;
        }

        if (delivery != DeliveryResult.SENT && delivery != DeliveryResult.QUEUED) {
            pending.remove(requestId);
            pendingPlayers.remove(requestId);
            pendingDestinations.remove(requestId);
            pendingOptions.remove(requestId);
            outbound.remove(player, requestId);

            return completed(TeleportStatus.SERVER_UNAVAILABLE, "The proxy connection is not available");
        }

        return result.completeOnTimeout(
            new TeleportResult(TeleportStatus.TIMED_OUT, "The cross-server teleport timed out"),
            TRANSFER_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        ).whenComplete((answer, throwable) -> {
            pending.remove(requestId);
            pendingPlayers.remove(requestId);
            pendingDestinations.remove(requestId);
            pendingOptions.remove(requestId);
            outbound.remove(player, requestId);
            if (answer == null || !answer.isSuccess()) {
                abort(new TeleportAbort(requestId, player.getUniqueId()));
                if (options.admission() != null) {
                    admissions.finish(options.admission(), false);
                }
            }
        });
    }

    private CompletableFuture<TeleportResult> completed(final TeleportStatus status, final String message) {
        return CompletableFuture.completedFuture(new TeleportResult(status, message));
    }

    @RequiredArgsConstructor
    private static final class Preparation {

        private final TeleportPreparation request;
        private final VexPlayer player;
        private final CompletableFuture<Boolean> ready = new CompletableFuture<>();
        private final CompletableFuture<Void> work = new CompletableFuture<>();
        private volatile boolean cancelled;
        private volatile boolean published;
        private volatile boolean retiring;
        private CompletableFuture<Void> recovery;
    }
}
