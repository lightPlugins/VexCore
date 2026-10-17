package dev.vexsoft.core.paper.service.effect;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.effect.RingBurst;
import org.bukkit.entity.Player;

/** Shared owner-bound particle effects, independent of consuming gameplay systems. */
public interface ParticleEffectService extends VexService {

    /** Expands a ring with optional rising fountains from the player's feet; replaces and retires owned animations. */
    void ringBurst(Player player, RingBurst settings);
}
