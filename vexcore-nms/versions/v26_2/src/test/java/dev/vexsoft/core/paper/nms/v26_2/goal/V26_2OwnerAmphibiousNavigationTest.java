package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks the native block states eligible for the campfire surface correction. */
public final class V26_2OwnerAmphibiousNavigationTest {

    @BeforeAll
    static void bootstrapBlocks() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void acceptsBothDryExtinguishedCampfireTypes() {
        for (var block : new Block[] {Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}) {
            assertTrue(V26_2OwnerAmphibiousNavigation.isDryUnlitCampfire(
                block.defaultBlockState().setValue(CampfireBlock.LIT, false)));
        }
    }

    @Test
    void leavesFireAndWaterToNativeNavigation() {
        assertFalse(V26_2OwnerAmphibiousNavigation.isDryUnlitCampfire(Blocks.CAMPFIRE.defaultBlockState()));
        assertFalse(V26_2OwnerAmphibiousNavigation.isDryUnlitCampfire(Blocks.SOUL_CAMPFIRE.defaultBlockState()));
        assertFalse(V26_2OwnerAmphibiousNavigation.isDryUnlitCampfire(
            Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)
                .setValue(CampfireBlock.WATERLOGGED, true)));
        assertFalse(V26_2OwnerAmphibiousNavigation.isDryUnlitCampfire(Blocks.STONE.defaultBlockState()));
        assertFalse(V26_2OwnerAmphibiousNavigation.isDryUnlitCampfire(Blocks.AIR.defaultBlockState()));
    }
}
