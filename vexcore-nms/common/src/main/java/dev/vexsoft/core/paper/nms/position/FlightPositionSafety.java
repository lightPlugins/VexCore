package dev.vexsoft.core.paper.nms.position;

import dev.vexsoft.core.api.mob.flight.FlightObstacles;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;

/** Measures flight clearance against nearby terrain without selecting roofs above a carrier. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FlightPositionSafety {

    /** Returns the required feet height, or NaN when the column is unavailable. */
    public static double minimumY(final Location position, final double clearance) {
        return minimumY(position, clearance, FlightObstacles.SOLID);
    }

    /** Measures terrain clearance while respecting an explicit order's leaf exclusion. */
    public static double minimumY(final Location position, final double clearance, final FlightObstacles obstacles) {
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
            if (!passable(block, obstacles)) {
                return block.getBoundingBox().getMaxY() + clearance;
            }
        }
        return position.getY();
    }

    /** Returns a local terrain-relative target height, or NaN over unavailable terrain or a void. */
    public static double hoverY(final Location position, final double height) {
        World world = position.getWorld();
        if (world == null || !world.isChunkLoaded(position.getBlockX() >> 4, position.getBlockZ() >> 4)
            || position.getY() < world.getMinHeight() || position.getY() >= world.getMaxHeight()) {
            return Double.NaN;
        }
        int top = Math.min(world.getMaxHeight() - 1, position.getBlockY());
        for (int y = top; y >= Math.max(world.getMinHeight(), top - 32); y--) {
            var block = world.getBlockAt(position.getBlockX(), y, position.getBlockZ());
            if (block.isLiquid()) {
                return y + 1.0D + height;
            }
            if (!block.isPassable()) {
                return block.getBoundingBox().getMaxY() + height;
            }
        }
        return Double.NaN;
    }

    /** Checks all blocks occupied by an airborne carrier before a recovery teleport. */
    public static boolean isClear(final Location feet, final double width, final double height) {
        return isClear(feet, width, height, FlightObstacles.SOLID);
    }

    /** Checks the entire body against the same solid-block policy used by the flight motor. */
    public static boolean isClear(final Location feet, final double width, final double height,
        final FlightObstacles obstacles) {
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
                    if (!passable(block, obstacles) || block.isLiquid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean passable(final Block block, final FlightObstacles obstacles) {
        return block.isPassable()
            || obstacles == FlightObstacles.IGNORE_LEAVES && block.getBlockData() instanceof Leaves;
    }
}
