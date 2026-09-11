package dev.vexsoft.core.paper.nms.position;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Shared block-level safety checks for native navigation and spawner placement. */
public final class GroundPositionSafety {

    private static final Set<Material> HAZARDS = Set.of(
        Material.LAVA,
        Material.FIRE,
        Material.SOUL_FIRE,
        Material.MAGMA_BLOCK,
        Material.CACTUS,
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE,
        Material.SWEET_BERRY_BUSH
    );

    private GroundPositionSafety() {
    }

    /** Returns whether the supplied ground and vertical clearance satisfy all rules. */
    public static boolean isSafe(
        final World world,
        final int blockX,
        final int y,
        final int blockZ,
        final int clearanceBlocks,
        final boolean avoidFluids,
        final boolean avoidHazards,
        final Set<Material> allowedSupportBlocks,
        final Set<Material> deniedSupportBlocks
    ) {
        Block support = world.getBlockAt(blockX, y - 1, blockZ);
        Material supportType = support.getType();

        if (!supportType.isSolid() || deniedSupportBlocks.contains(supportType) || (!allowedSupportBlocks.isEmpty()
            && !allowedSupportBlocks.contains(supportType)) || avoidHazards && HAZARDS.contains(supportType)) {
            return false;
        }

        for (int offset = 0; offset < clearanceBlocks; offset++) {
            Block space = world.getBlockAt(blockX, y + offset, blockZ);

            if (!space.isPassable() || avoidFluids && space.isLiquid()) {
                return false;
            }
        }

        return true;
    }
}
