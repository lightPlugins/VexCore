package dev.vexsoft.core.paper.packets.v26_2.interactiveui;

import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput.Kind;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

/** Decodes only native gameplay input; connection, chat, and teleport confirmation packets pass through. */
@UtilityClass
public class V26_2InteractiveUiInputDecoder {

    public static Optional<InteractiveUiPacketInput> decode(final Object packet) {
        if (packet instanceof ServerboundMovePlayerPacket movement) {
            return Optional.of(new InteractiveUiPacketInput(
                movement.hasRotation() ? Kind.ROTATION : Kind.OTHER_GAMEPLAY,
                movement.getYRot(0.0F),
                movement.getXRot(0.0F),
                -1,
                0,
                0,
                0
            ));
        }

        if (packet instanceof ServerboundPlayerActionPacket action) {
            Kind kind = switch (action.getAction()) {
                case START_DESTROY_BLOCK -> Kind.PRESS;
                case ABORT_DESTROY_BLOCK -> Kind.RELEASE;
                case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> Kind.EXIT;
                default -> Kind.BLOCK_ACTION;
            };
            BlockPos position = action.getPos();
            int sequence = action.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || action.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                    ? action.getSequence() : -1;

            return Optional.of(new InteractiveUiPacketInput(
                kind,
                0.0F,
                0.0F,
                sequence,
                position.getX(),
                position.getY(),
                position.getZ()
            ));
        }

        if (packet instanceof ServerboundPlayerInputPacket input) {
            return input(input.input().shift() ? Kind.EXIT : Kind.OTHER_GAMEPLAY, -1);
        }

        if (packet instanceof ServerboundUseItemPacket useItem) {
            return input(Kind.EXIT, useItem.getSequence());
        }

        if (packet instanceof ServerboundUseItemOnPacket useItemOn) {
            return input(Kind.EXIT, useItemOn.getSequence());
        }

        if (packet instanceof ServerboundSetCarriedItemPacket
            || packet instanceof ServerboundContainerClickPacket
            || packet instanceof ServerboundContainerClosePacket
            || packet instanceof ServerboundContainerButtonClickPacket
            || packet instanceof ServerboundSetCreativeModeSlotPacket
            || packet instanceof ServerboundPlaceRecipePacket) {
            return input(Kind.EXIT, -1);
        }

        if (packet instanceof ServerboundAttackPacket
            || packet instanceof ServerboundInteractPacket
            || packet instanceof ServerboundSwingPacket
            || packet instanceof ServerboundMoveVehiclePacket
            || packet instanceof ServerboundPaddleBoatPacket
            || packet instanceof ServerboundPlayerAbilitiesPacket
            || packet instanceof ServerboundPlayerCommandPacket
            || packet instanceof ServerboundPickItemFromBlockPacket
            || packet instanceof ServerboundPickItemFromEntityPacket) {
            return input(Kind.OTHER_GAMEPLAY, -1);
        }

        return Optional.empty();
    }

    private static Optional<InteractiveUiPacketInput> input(final Kind kind, final int sequence) {
        return Optional.of(new InteractiveUiPacketInput(kind, 0.0F, 0.0F, sequence, 0, 0, 0));
    }
}
