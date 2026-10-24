package dev.vexsoft.core.paper.packets.v26_2.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Verifies real native correction packets cannot undo a predicted click while its durable exchange is pending. */
final class V26_2InventorySynchronizationTest {

    @BeforeAll
    static void bootstrap() {
        V26_2ItemMetaTransformerTest.bootstrap();
    }

    @Test
    void holdsCursorAndMenuCorrectionsUntilExactlyOneSettlement() {
        var synchronization = new V26_2InventorySynchronization();
        var viewer = UUID.randomUUID();
        var completed = new AtomicInteger();
        var finish = synchronization.hold(viewer, 4, completed::incrementAndGet);
        for (Object packet : List.of(
            new ClientboundContainerSetSlotPacket(4, 1, 13, ItemStack.EMPTY),
            new ClientboundContainerSetContentPacket(4, 1, List.of(), ItemStack.EMPTY),
            new ClientboundSetCursorItemPacket(ItemStack.EMPTY),
            new ClientboundSetPlayerInventoryPacket(2, ItemStack.EMPTY)
        )) {
            assertNull(synchronization.filter(viewer, packet));
            assertSame(packet, synchronization.filter(UUID.randomUUID(), packet));
        }
        var otherMenu = new ClientboundContainerSetSlotPacket(8, 1, 13, ItemStack.EMPTY);
        assertSame(otherMenu, synchronization.filter(viewer, otherMenu));
        assertEquals(0, completed.get());

        finish.run();
        finish.run();

        assertEquals(1, completed.get());
        var cursor = new ClientboundSetCursorItemPacket(ItemStack.EMPTY);
        assertSame(cursor, synchronization.filter(viewer, cursor));
    }

    @Test
    void closingAndDisconnectFenceLateSettlementAgainstANewerMenu() {
        var synchronization = new V26_2InventorySynchronization();
        var viewer = UUID.randomUUID();
        var completed = new AtomicInteger();
        var old = synchronization.hold(viewer, 4, completed::incrementAndGet);
        var close = new ClientboundContainerClosePacket(4);
        assertSame(close, synchronization.filter(viewer, close));
        var current = synchronization.hold(viewer, 5, completed::incrementAndGet);

        old.run();

        assertEquals(0, completed.get());
        assertNull(synchronization.filter(viewer, new ClientboundSetCursorItemPacket(ItemStack.EMPTY)));
        synchronization.clear(viewer);
        current.run();
        assertEquals(0, completed.get());
    }
}
