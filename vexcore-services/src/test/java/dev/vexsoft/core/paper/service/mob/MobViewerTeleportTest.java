package dev.vexsoft.core.paper.service.mob;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

public final class MobViewerTeleportTest {

    private final Player player = (Player) Proxy.newProxyInstance(
        Player.class.getClassLoader(),
        new Class<?>[]{Player.class},
        (proxy, method, args) -> null
    );

    @Test
    public void repeatedDialogueCorrectionsAndCameraRotationDoNotResetNpcs() {
        Location from = new Location(null, 20.5, 64, -30.5);

        for (int iterationIndex = 0; iterationIndex < 1000; iterationIndex++) {
            Location corrected = from.clone();

            corrected.setYaw(iterationIndex % 360);
            corrected.setPitch(iterationIndex % 90);

            assertFalse(MobViewerTeleport.changesPosition(new PlayerTeleportEvent(
                player,
                from,
                corrected,
                PlayerTeleportEvent.TeleportCause.PLUGIN
            )));
        }
    }

    @Test
    public void realRelocationStillResetsEvenForSmallChangesOrTheSameCoordinatesInAnotherWorld() {
        Location from = new Location(null, 20.5, 64, -30.5);

        for (Location to : new Location[]{from.clone().add(0.00001, 0, 0), from.clone().add(
            0,
            1,
            0
        ), from.clone().add(0, 0, 1000)}) {
            assertTrue(MobViewerTeleport.changesPosition(new PlayerTeleportEvent(
                player,
                from,
                to,
                PlayerTeleportEvent.TeleportCause.PLUGIN
            )));
        }

        World world = (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[]{World.class},
            (proxy, method, args) -> null
        );
        Location otherWorld = from.clone();

        otherWorld.setWorld(world);

        assertTrue(MobViewerTeleport.changesPosition(new PlayerTeleportEvent(player, from, otherWorld)));

        var cancelled = new PlayerTeleportEvent(player, from, from.clone().add(10, 0, 0));

        cancelled.setCancelled(true);

        assertFalse(MobViewerTeleport.changesPosition(cancelled));
    }
}
