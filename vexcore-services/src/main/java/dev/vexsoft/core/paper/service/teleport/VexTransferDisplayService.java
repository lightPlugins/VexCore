package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TransferPresentation;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;

/** Renders carried Components on entity schedulers and never persists a native UI object. */
@Dependencies(ScheduleService.class)
public final class VexTransferDisplayService implements TransferDisplayService, AutoCloseable {

    private final ScheduleService schedules;
    private final Map<VexPlayer, Display> displays = new ConcurrentHashMap<>();

    public VexTransferDisplayService(final VexServiceRegistry services) {
        schedules = services.require(ScheduleService.class);
    }

    @Override
    public void show(final VexPlayer owner, final UUID request, final TransferPresentation presentation) {
        if (presentation == null) {
            return;
        }
        owner.findPlatformPlayer(Player.class).ifPresent(player -> schedules.runFor(player, () -> {
            if (owner.findPlatformPlayer(Player.class).orElse(null) != player || !player.isConnected()) {
                return;
            }
            var bar = BossBar.bossBar(GsonComponentSerializer.gson().deserialize(presentation.loadingTitle()),
                1, BossBar.Color.valueOf(presentation.color()), BossBar.Overlay.PROGRESS);
            var previous = displays.put(owner, new Display(request, player, bar, presentation));
            if (previous != null) {
                schedules.runFor(previous.player(), () -> previous.player().hideBossBar(previous.bar()));
            }
            player.showBossBar(bar);
        }));
    }

    @Override
    public void finish(final VexPlayer owner, final UUID request, final boolean successful) {
        if (owner.findPlatformPlayer(Player.class).isEmpty()) {
            displays.remove(owner);
            return;
        }
        owner.findPlatformPlayer(Player.class).ifPresent(player -> schedules.runFor(player, () -> {
            var display = displays.get(owner);
            if (display == null || !display.request().equals(request) || display.player() != player) {
                return;
            }
            if (successful) {
                remove(owner, display);
            } else {
                display.bar().name(GsonComponentSerializer.gson().deserialize(display.presentation().failedTitle()));
                display.bar().color(BossBar.Color.RED);
                schedules.runForLater(player, 40, () -> remove(owner, display), () -> displays.remove(owner, display));
            }
        }, () -> displays.remove(owner)));
    }

    @Override
    public void disconnected(final VexPlayer owner) {
        var display = displays.remove(owner);
        if (display != null) {
            schedules.runFor(display.player(), () -> display.player().hideBossBar(display.bar()));
        }
    }

    private void remove(final VexPlayer owner, final Display display) {
        if (displays.remove(owner, display)) {
            display.player().hideBossBar(display.bar());
        }
    }

    @Override
    public void close() {
        displays.forEach((owner, display) -> schedules.runFor(display.player(),
            () -> display.player().hideBossBar(display.bar())));
        displays.clear();
    }

    private record Display(UUID request, Player player, BossBar bar, TransferPresentation presentation) {
    }
}
