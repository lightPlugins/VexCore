package dev.vexsoft.core.paper.packets.v26_2.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.datafixers.util.Pair;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaLookup;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks colored world/held item projection while preserving the server's authoritative item stack. */
final class V26_2ItemMetaWorldPacketTest {

    private final V26_2ItemMetaPacketRewriter rewriter = new V26_2ItemMetaPacketRewriter();
    private final UUID viewer = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() {
        V26_2ItemMetaTransformerTest.bootstrap();
    }

    @Test
    void rewritesDroppedItemsFramesAndDisplaysWithoutTouchingUnrelatedMetadata() {
        ItemStack original = item();
        var flags = new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) 0);
        var model = new SynchedEntityData.DataValue<>(8, EntityDataSerializers.ITEM_STACK, original);
        var packet = new ClientboundSetEntityDataPacket(42, List.of(flags, model));

        var result = (ClientboundSetEntityDataPacket) rewriter.rewrite(viewer, packet, lookup(true));

        assertNotSame(packet, result);
        assertEquals(42, result.id());
        assertSame(flags, result.packedItems().getFirst());
        var shown = (ItemStack) result.packedItems().get(1).value();
        assertColored(shown);
        assertTrue(original.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        assertEquals(Identifier.parse("minecraft:iron_sword"), original.get(DataComponents.ITEM_MODEL));
        assertSame(original, packet.packedItems().get(1).value());
        assertSame(packet, rewriter.rewrite(viewer, packet, lookup(false)));
    }

    @Test
    void rewritesTheInitialTrackingBundleAsWellAsLaterMetadataUpdates() {
        var metadata = new ClientboundSetEntityDataPacket(42,
            List.of(new SynchedEntityData.DataValue<>(8, EntityDataSerializers.ITEM_STACK, item())));
        var packet = new ClientboundBundlePacket(List.of(metadata));

        var result = (ClientboundBundlePacket) rewriter.rewrite(viewer, packet, lookup(true));

        var child = (ClientboundSetEntityDataPacket) result.subPackets().iterator().next();
        assertColored((ItemStack) child.packedItems().getFirst().value());
        assertTrue(((ItemStack) metadata.packedItems().getFirst().value()).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        assertSame(packet, rewriter.rewrite(viewer, packet, lookup(false)));
    }

    @Test
    void rewritesOtherPlayersHeldItemsWithoutMutatingTheirEquipment() {
        ItemStack original = item();
        var packet = new ClientboundSetEquipmentPacket(42, List.of(Pair.of(EquipmentSlot.MAINHAND, original)));

        var result = (ClientboundSetEquipmentPacket) rewriter.rewrite(viewer, packet, lookup(true));

        assertEquals(EquipmentSlot.MAINHAND, result.getSlots().getFirst().getFirst());
        assertColored(result.getSlots().getFirst().getSecond());
        assertTrue(original.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        assertSame(packet, rewriter.rewrite(viewer, packet, lookup(false)));
    }

    private static ItemStack item() {
        var stack = new ItemStack(Items.IRON_SWORD);
        stack.set(DataComponents.ITEM_MODEL, Identifier.parse("minecraft:iron_sword"));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static void assertColored(final ItemStack stack) {
        assertEquals(Identifier.parse("isles:rarity_glint/rare/minecraft/iron_sword"),
            stack.get(DataComponents.ITEM_MODEL));
        assertFalse(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
    }

    private static FakeItemMetaLookup lookup(final boolean enabled) {
        return new FakeItemMetaLookup() {
            @Override
            public Optional<FakeItemMetaRule> find(final UUID viewerId,
                                                   final org.bukkit.inventory.ItemStack stack) {
                return enabled ? Optional.of(FakeItemMetaRule.builder()
                    .itemModel(new NamespacedKey("isles", "rarity_glint/rare/minecraft/iron_sword"))
                    .enchantmentGlint(false).build()) : Optional.empty();
            }

            @Override
            public boolean hasAny(final UUID viewerId) {
                return enabled;
            }
        };
    }
}
