package dev.vexsoft.core.paper.upgrade;

import dev.vexsoft.core.api.teleport.TransferPresentation.Sound;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bounded tick timings and event sound sequences shared by Arkie and item upgrade rituals. */
public record UpgradeAnimationSettings(
    int chargeTicks, int holdTicks, int finishTicks, int resolutionTimeoutTicks,
    Map<String, List<UpgradeAnimationSound>> sounds, double instantSuccessChance, List<UpgradeAnimationPacing> pacing
) {

    /** Preserves fixed-charge callers without additional visual breakthrough checks or chance-dependent pacing. */
    public UpgradeAnimationSettings(
        int chargeTicks, int holdTicks, int finishTicks, int resolutionTimeoutTicks,
        Map<String, List<UpgradeAnimationSound>> sounds
    ) {
        this(chargeTicks, holdTicks, finishTicks, resolutionTimeoutTicks, sounds, 0, List.of());
    }

    public UpgradeAnimationSettings {
        if (chargeTicks < 40 || chargeTicks > 400 || holdTicks < 1 || holdTicks > 100
            || finishTicks < 20 || finishTicks > 200 || resolutionTimeoutTicks < 20
            || resolutionTimeoutTicks > 1200) {
            throw new IllegalArgumentException("Upgrade animation timings exceed supported bounds");
        }
        var copy = new LinkedHashMap<String, List<UpgradeAnimationSound>>();
        if (sounds != null) {
            sounds.forEach((event, cues) -> {
                if (!List.of("start", "step", "rune", "hold", "success", "failure").contains(event)
                    || cues == null || cues.size() > 8) {
                    throw new IllegalArgumentException("Invalid upgrade animation sound event or sequence");
                }
                copy.put(event, List.copyOf(cues));
            });
        }
        sounds = Map.copyOf(copy);
        if (!Double.isFinite(instantSuccessChance) || instantSuccessChance < 0 || instantSuccessChance > 1) {
            throw new IllegalArgumentException("Visual breakthrough chance must be within 0..1");
        }
        pacing = pacing == null ? List.of() : pacing.stream()
            .sorted(Comparator.comparingDouble(UpgradeAnimationPacing::minimumSuccessChance).reversed()).toList();
        if (!pacing.isEmpty() && (pacing.getLast().minimumSuccessChance() != 0
            || pacing.stream().map(UpgradeAnimationPacing::minimumSuccessChance).distinct().count() != pacing.size()
            || pacing.size() > 16)) {
            throw new IllegalArgumentException("Upgrade pacing thresholds must be unique and include a zero fallback");
        }
    }

    /** Selects the highest matching visual pacing threshold without changing the authoritative success chance. */
    public Optional<UpgradeAnimationPacing> pacingFor(double successChance) {
        if (!Double.isFinite(successChance) || successChance < 0 || successChance > 1) {
            throw new IllegalArgumentException("Upgrade success chance must be within 0..1");
        }
        return pacing.stream().filter(profile -> successChance >= profile.minimumSuccessChance()).findFirst();
    }

    /** Returns a slower charge with layered vanilla sounds and a short pause before the final success reveal. */
    public static UpgradeAnimationSettings defaults() {
        return new UpgradeAnimationSettings(220, 24, 44, 200, Map.of(
            "start", List.of(cue(0, "block.beacon.activate", 0.65F, 0.65F),
                cue(5, "block.amethyst_block.resonate", 0.55F, 0.7F)),
            "step", List.of(cue(0, "block.amethyst_block.chime", 0.5F, 1.1F)),
            "rune", List.of(cue(0, "block.respawn_anchor.charge", 0.5F, 1.4F)),
            "hold", List.of(cue(0, "block.beacon.power_select", 0.45F, 1.6F)),
            "success", List.of(cue(0, "block.end_portal.spawn", 0.3F, 1.7F),
                cue(3, "ui.toast.challenge_complete", 0.65F, 1.25F),
                cue(12, "block.amethyst_block.resonate", 0.6F, 1.5F)),
            "failure", List.of(cue(0, "block.beacon.deactivate", 0.6F, 0.8F),
                cue(5, "block.amethyst_cluster.break", 0.45F, 0.65F))
        ), 0.15, List.of(
            new UpgradeAnimationPacing(0.9, 2, 3, 60, 80, 0, 0),
            new UpgradeAnimationPacing(0.6, 3, 5, 100, 140, 0, 0),
            new UpgradeAnimationPacing(0.3, 5, 7, 140, 180, 0, 0),
            new UpgradeAnimationPacing(0, 7, 9, 200, 240, 60, 80)
        ));
    }

    private static UpgradeAnimationSound cue(int delay, String type, float volume, float pitch) {
        return new UpgradeAnimationSound(delay, new Sound("minecraft:" + type, volume, pitch));
    }
}
