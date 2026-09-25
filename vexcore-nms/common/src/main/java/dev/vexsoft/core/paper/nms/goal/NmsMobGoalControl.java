package dev.vexsoft.core.paper.nms.goal;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;

/** Shared pause flag for VexCore goals while another system steers a mob. */
public final class NmsMobGoalControl {

    private static final Set<UUID> PAUSED = ConcurrentHashMap.newKeySet();

    private NmsMobGoalControl() {
    }

    public static void setPaused(final Entity mob, final boolean paused) {
        if (paused) {
            PAUSED.add(mob.getUniqueId());
        } else {
            PAUSED.remove(mob.getUniqueId());
        }
    }

    public static boolean isPaused(final Entity mob) {
        return PAUSED.contains(mob.getUniqueId());
    }
}
