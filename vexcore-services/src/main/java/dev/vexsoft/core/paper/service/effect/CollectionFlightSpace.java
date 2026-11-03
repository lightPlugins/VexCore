package dev.vexsoft.core.paper.service.effect;

import dev.vexsoft.core.paper.effect.CollectionBlockView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Bounded, conservative swept-volume checks; uncertain or foreign-region space is never treated as empty. */
final class CollectionFlightSpace {

    static final float ITEM_SCALE = 0.38F;
    private static final double RADIUS = Math.sqrt(3) * ITEM_SCALE * 0.5 + 0.005;
    private static final int MAX_BLOCKS = 96;

    static Launch launch(Location origin, Location target, CollectionBlockView view, Vector scatter) {
        var toward = target.toVector().subtract(origin.toVector());
        var directions = new ArrayList<>(List.of(new Vector(0, 1, 0), new Vector(1, 0, 0),
            new Vector(-1, 0, 0), new Vector(0, 0, 1), new Vector(0, 0, -1)));
        directions.sort(Comparator.comparingDouble((Vector direction) -> direction.dot(toward)).reversed());
        for (var direction : directions) {
            Vector tangent = scatter.clone().subtract(direction.clone().multiply(scatter.dot(direction)));
            Location start = origin.clone().add(direction.clone().multiply(0.5 + RADIUS + 0.04))
                .add(tangent.clone().multiply(0.12));
            Location end = start.clone().add(direction).add(tangent.multiply(0.8));
            if (direction.getY() == 0) {
                end.add(0, 0.25, 0);
            }
            if (clear(start, end, view)) {
                return new Launch(start, end, false);
            }
        }
        // This is cosmetic: an enclosed resource still gets its own flight instead of disappearing silently.
        Location start = origin.clone().add(scatter.clone().multiply(0.08));
        Location end = origin.clone().add(scatter).add(0, 0.8, 0);
        return available(start, end) ? new Launch(start, end, true) : null;
    }

    static boolean available(Location from, Location to) {
        var world = from.getWorld();
        if (world == null || world != to.getWorld()
            || Math.min(from.y(), to.y()) < world.getMinHeight()
            || Math.max(from.y(), to.y()) >= world.getMaxHeight()) {
            return false;
        }
        int minX = (int) Math.floor(Math.min(from.x(), to.x()) - RADIUS) >> 4;
        int maxX = (int) Math.floor(Math.max(from.x(), to.x()) + RADIUS) >> 4;
        int minZ = (int) Math.floor(Math.min(from.z(), to.z()) - RADIUS) >> 4;
        int maxZ = (int) Math.floor(Math.max(from.z(), to.z()) + RADIUS) >> 4;
        if (maxX - minX > 1 || maxZ - minZ > 1) {
            return false;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x, z) || !Bukkit.isOwnedByCurrentRegion(world, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean clear(Location from, Location to, CollectionBlockView view) {
        var world = from.getWorld();
        if (world == null || world != to.getWorld()) {
            return false;
        }
        int minX = (int) Math.floor(Math.min(from.x(), to.x()) - RADIUS);
        // Fences and walls can collide above their own voxel, so also inspect the supporting row.
        int minY = (int) Math.floor(Math.min(from.y(), to.y()) - RADIUS) - 1;
        int minZ = (int) Math.floor(Math.min(from.z(), to.z()) - RADIUS);
        int maxX = (int) Math.floor(Math.max(from.x(), to.x()) + RADIUS);
        int maxY = (int) Math.floor(Math.max(from.y(), to.y()) + RADIUS);
        int maxZ = (int) Math.floor(Math.max(from.z(), to.z()) + RADIUS);
        if (minY < world.getMinHeight() || maxY >= world.getMaxHeight()
            || (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > MAX_BLOCKS) {
            return false;
        }
        // Validate the entire swept volume before any live block access; never load a chunk for cosmetics.
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                if (!world.isChunkLoaded(x, z) || !Bukkit.isOwnedByCurrentRegion(world, x, z)) {
                    return false;
                }
            }
        }
        Vector start = from.toVector();
        Vector direction = to.toVector().subtract(start);
        double length = direction.length();
        if (length > 0.00001) {
            direction.multiply(1 / length);
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var override = view.find(x, y, z);
                    BoundingBox box;
                    if (override != null) {
                        Material material = override.getMaterial();
                        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                            continue;
                        }
                        // A full voxel also safely covers rotation and partial client-side replacement shapes.
                        box = new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
                    } else {
                        var block = world.getBlockAt(x, y, z);
                        if (block.isPassable() && !block.isLiquid()) {
                            continue;
                        }
                        box = block.getBoundingBox();
                    }
                    box.expand(RADIUS);
                    if (box.contains(start) || length > 0.00001 && box.rayTrace(start, direction, length) != null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    record Launch(Location start, Location end, boolean throughTerrain) {
    }
}
