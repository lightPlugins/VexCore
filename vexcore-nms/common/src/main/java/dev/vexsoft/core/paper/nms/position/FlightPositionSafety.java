package dev.vexsoft.core.paper.nms.position;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;

/** Measures flight clearance against nearby terrain without selecting roofs above a carrier. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FlightPositionSafety {

    /** Returns the required feet height, or NaN when the column is unavailable. */
    public static double minimumY(final Location position, final double clearance) {
        World world = position.getWorld();
        int x = position.getBlockX();
        int z = position.getBlockZ();
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)
            || position.getY() < world.getMinHeight() || position.getY() >= world.getMaxHeight()) {
            return Double.NaN;
        }
        int top = Math.min(world.getMaxHeight() - 1, position.getBlockY());
        int bottom = Math.max(world.getMinHeight(), top - (int) Math.ceil(clearance) - 2);
        for (int y = top; y >= bottom; y--) {
            var block = world.getBlockAt(x, y, z);
            if (block.isLiquid()) {
                return y + 1.0D + clearance;
            }
            if (!block.isPassable()) {
                return block.getBoundingBox().getMaxY() + clearance;
            }
        }
        return position.getY();
    }

    /** Checks all blocks occupied by an airborne carrier before a recovery teleport. */
    public static boolean isClear(final Location feet, final double width, final double height) {
        World world = feet.getWorld();
        int minimumX = (int) Math.floor(feet.getX() - width / 2.0D);
        int maximumX = (int) Math.floor(feet.getX() + width / 2.0D - 1.0E-6D);
        int minimumZ = (int) Math.floor(feet.getZ() - width / 2.0D);
        int maximumZ = (int) Math.floor(feet.getZ() + width / 2.0D - 1.0E-6D);
        int maximumY = (int) Math.floor(feet.getY() + height - 1.0E-6D);
        if (world == null || feet.getY() < world.getMinHeight() || maximumY >= world.getMaxHeight()) {
            return false;
        }
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    return false;
                }
                for (int y = feet.getBlockY(); y <= maximumY; y++) {
                    var block = world.getBlockAt(x, y, z);
                    if (!block.isPassable() || block.isLiquid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
