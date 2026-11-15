package dev.vexsoft.core.paper.packets.v26_3.interactiveui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class V26_3InteractiveUiInputDecoderTest {

    @Test
    void preservesMountedRotationWhileClassifyingPositionPacketsForConsumption() {
        InteractiveUiPacketInput rotation = decode(new ServerboundMovePlayerPacket.Rot(25.0F, -15.0F, true, false));

        assertEquals(Kind.ROTATION, rotation.kind());
        assertEquals(25.0F, rotation.yaw());
        assertEquals(-15.0F, rotation.pitch());
        assertEquals(-1, rotation.sequence());
        assertEquals(Kind.OTHER_GAMEPLAY, decode(new ServerboundMovePlayerPacket.Pos(Vec3.ZERO, true, false)).kind());
    }

    @Test
    void preservesFakeBlockCoordinatesAndLeavesDirectionChangesAndAbortsUnsequenced() {
        BlockPos target = new BlockPos(-17, 70, 32);
        InteractiveUiPacketInput press = decode(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, target, Direction.NORTH, 24
        ));
        InteractiveUiPacketInput release = decode(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, target, Direction.DOWN
        ));

        InteractiveUiPacketInput directionChange = decode(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.CHANGE_DESTROY_DIRECTION, target, Direction.SOUTH
        ));

        assertEquals(Kind.PRESS, press.kind());
        assertEquals(24, press.sequence());
        assertEquals(-17, press.blockX());
        assertEquals(70, press.blockY());
        assertEquals(32, press.blockZ());
        assertEquals(Kind.RELEASE, release.kind());
        assertEquals(-1, release.sequence());
        assertEquals(Kind.BLOCK_ACTION, directionChange.kind());
        assertEquals(-1, directionChange.sequence());
    }

    @Test
    void distinguishesExitAndItemPredictionFromUnrelatedConnectionPackets() {
        assertEquals(Kind.OTHER_GAMEPLAY, decode(ServerboundPunchPacket.INSTANCE).kind());
        assertEquals(Kind.EXIT, decode(new ServerboundSetCarriedItemPacket(3)).kind());
        assertEquals(Kind.EXIT, decode(new ServerboundPlayerInputPacket(
            new Input(false, false, false, false, false, true, false)
        )).kind());
        assertEquals(91, decode(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, 91, 12.0F, 9.0F)).sequence());
        var teleport = new ServerboundAcceptTeleportationPacket(42, 1.0, 64.0, 2.0, 25.0F, -15.0F);
        assertTrue(V26_3InteractiveUiInputDecoder.decode(teleport).isEmpty());
        assertTrue(V26_3InteractiveUiInputDecoder.decode(new ServerboundKeepAlivePacket(123L)).isEmpty());
        assertTrue(V26_3InteractiveUiInputDecoder.decode(new Object()).isEmpty());
    }

    @Test
    void cameraInsideTheFakeCubeProducesAnImmediateInsideHit() {
        BlockPos target = new BlockPos(4, 66, -8);
        Vec3 camera = Vec3.atCenterOf(target);
        BlockHitResult hit = Shapes.block().clip(camera, camera.add(0.0, 0.0, 4.5), target);

        assertTrue(hit != null && hit.isInside());
        assertEquals(target, hit.getBlockPos());
        assertEquals(Direction.NORTH, hit.getDirection());
        assertFalse(hit.getLocation().distanceToSqr(camera) > 0.01);
    }

    private static InteractiveUiPacketInput decode(final Object packet) {
        return V26_3InteractiveUiInputDecoder.decode(packet).orElseThrow();
    }
}
