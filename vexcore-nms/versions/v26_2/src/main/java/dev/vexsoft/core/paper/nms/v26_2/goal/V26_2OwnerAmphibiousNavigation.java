package dev.vexsoft.core.paper.nms.v26_2.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/** Retains amphibious routing while resolving dry campfire surfaces above their occupied block. */
public final class V26_2OwnerAmphibiousNavigation extends AmphibiousPathNavigation {

    public V26_2OwnerAmphibiousNavigation(final Mob mob, final Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(final int maxVisitedNodes) {
        nodeEvaluator = new CampfireNodeEvaluator();

        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected double getGroundY(final Vec3 target) {
        BlockPos position = BlockPos.containing(target);

        if (!mob.isInWater() && isDryUnlitCampfire(level.getBlockState(position.below()))) {
            return WalkNodeEvaluator.getFloorLevel(level, position);
        }

        return super.getGroundY(target);
    }

    static boolean isDryUnlitCampfire(final BlockState state) {
        return state.getBlock() instanceof CampfireBlock
            && !state.getValue(CampfireBlock.LIT) && !state.getValue(CampfireBlock.WATERLOGGED);
    }

    private static final class CampfireNodeEvaluator extends AmphibiousNodeEvaluator {

        private CampfireNodeEvaluator() {
            super(false);
        }

        @Override
        public Node getStart() {
            BlockPos position = mob.blockPosition();

            // Vanilla rounds y + 0.5 down into a campfire's 7/16-high collision shape.
            // The air node above it already uses the correct native floor and hazard checks.
            if (mob.onGround() && !mob.isInWater()
                && isDryUnlitCampfire(currentContext.getBlockState(position)) && canStartAt(position.above())) {
                return getStartNode(position.above());
            }

            return super.getStart();
        }

        @Override
        public Target getTarget(final double x, final double y, final double z) {
            BlockPos position = BlockPos.containing(x, y, z);

            if (isDryUnlitCampfire(currentContext.getBlockState(position))) {
                return super.getTarget(x, position.getY() + 1.0D, z);
            }

            return super.getTarget(x, y, z);
        }
    }
}
