package dev.vexsoft.core.paper.nms.v26_2.goal;

import dev.vexsoft.core.paper.nms.position.FlightPositionSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.bukkit.Location;

/** Routes native flyers above the configured terrain clearance rather than lifting an existing ground path. */
public final class V26_2FlightNavigation extends FlyingPathNavigation {

    private final double minimumHeight;

    public V26_2FlightNavigation(final Mob mob, final Level level, final double minimumHeight) {
        super(mob, level);
        this.minimumHeight = minimumHeight;
    }

    @Override
    protected PathFinder createPathFinder(final int maximumNodes) {
        nodeEvaluator = new FlyNodeEvaluator() {
            @Override
            protected Node findAcceptedNode(final int x, final int y, final int z) {
                double floor = floor(x, y, z);
                if (!Double.isFinite(floor)) {
                    return null;
                }
                if (y < floor) {
                    // Allow only an initial vertical ascent when the carrier spawned below its minimum.
                    if (x != mob.getBlockX() || z != mob.getBlockZ() || y <= mob.getY()) {
                        return null;
                    }
                }
                return super.findAcceptedNode(x, y, z);
            }
        };
        return new PathFinder(nodeEvaluator, maximumNodes);
    }

    @Override
    public Path createPath(final BlockPos target, final int reachRange) {
        double minimum = floor(target.getX(), target.getY(), target.getZ());
        if (!Double.isFinite(minimum)) {
            return null;
        }
        return super.createPath(new BlockPos(target.getX(), Math.max(target.getY(), (int) Math.ceil(minimum)),
            target.getZ()), reachRange);
    }

    private double floor(final int x, final int y, final int z) {
        return FlightPositionSafety.minimumY(new Location(mob.getBukkitEntity().getWorld(), x + 0.5D, y, z + 0.5D),
            minimumHeight);
    }

    public double minimumHeight() {
        return minimumHeight;
    }
}
