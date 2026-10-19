package dev.vexsoft.core.paper.packets.item;

import java.util.List;
import java.util.Objects;
import org.bukkit.NamespacedKey;

/** Client-only equipment assets for a repeating animation, with a canonical asset for Creative restoration. */
public record FakeItemEquipmentAnimation(NamespacedKey original, List<NamespacedKey> frames, int ticksPerFrame) {

    public FakeItemEquipmentAnimation {
        Objects.requireNonNull(original, "original");
        frames = List.copyOf(frames);
        if (frames.isEmpty() || frames.size() > 64 || ticksPerFrame < 1) {
            throw new IllegalArgumentException("Equipment animation needs 1..64 frames and a positive frame time");
        }
    }

    /** Selects a frame from the server's tick counter, including after counter overflow. */
    public NamespacedKey at(final int tick) {
        return frames.get(Math.floorMod(Math.floorDiv(tick, ticksPerFrame), frames.size()));
    }
}
