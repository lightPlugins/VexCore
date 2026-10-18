package dev.vexsoft.core.paper.packets.v26_2.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.datafixers.util.Pair;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaLookup;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaRule;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Separates native worn-armor glint from item glint for both the owner and tracking viewers. */
final class V26_2ArmorGlintPacketTest {

    private final V26_2ItemMetaPacketRewriter rewriter = new V26_2ItemMetaPacketRewriter();
    private final V26_2ItemMetaTransformer transformer = new V26_2ItemMetaTransformer();
    private final UUID viewer = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() {
        V26_2ItemMetaTransformerTest.bootstrap();
    }

    @Test
    void trackingViewersSeeNativeArmorGlintWhileHeldArmorKeepsItsItemPresentation() {
        var original = armor();
        var packet = new ClientboundSetEquipmentPacket(42, List.of(
            Pair.of(EquipmentSlot.CHEST, original), Pair.of(EquipmentSlot.MAINHAND, original)));
        var result = (ClientboundSetEquipmentPacket) rewriter.rewrite(viewer, packet, lookup(rule()));
        assertPresentation(result.getSlots().get(0).getSecond(), true);
        assertPresentation(result.getSlots().get(1).getSecond(), false);
        assertEquals(original.get(DataComponents.EQUIPPABLE), result.getSlots().get(0).getSecond()
            .get(DataComponents.EQUIPPABLE));
        assertEquals(Identifier.parse("isles:silver-violet-chestplate"), original.get(DataComponents.ITEM_MODEL));
        assertTrue(original.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
    }

    @Test
    void theOwnersFullInventoryUsesWornGlintOnlyInItsFourArmorSlots() {
        var items = new ArrayList<>(Collections.nCopies(46, ItemStack.EMPTY));
        for (int slot = InventoryMenu.ARMOR_SLOT_START; slot < InventoryMenu.ARMOR_SLOT_END; slot++) {
            items.set(slot, armor());
        }
        items.set(InventoryMenu.INV_SLOT_START, armor());
        var packet = new ClientboundContainerSetContentPacket(InventoryMenu.CONTAINER_ID, 7, items, armor());
        var result = (ClientboundContainerSetContentPacket) rewriter.rewrite(viewer, packet, lookup(rule()));
        for (int slot = InventoryMenu.ARMOR_SLOT_START; slot < InventoryMenu.ARMOR_SLOT_END; slot++) {
            assertPresentation(result.items().get(slot), true);
            assertTrue(items.get(slot).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        }
        assertPresentation(result.items().get(InventoryMenu.INV_SLOT_START), false);
        assertPresentation(result.carriedItem(), false);
        var otherMenu = new ClientboundContainerSetContentPacket(2, 7, items, ItemStack.EMPTY);
        var otherResult = (ClientboundContainerSetContentPacket) rewriter.rewrite(viewer, otherMenu, lookup(rule()));
        assertPresentation(otherResult.items().get(InventoryMenu.ARMOR_SLOT_START), false);
    }

    @Test
    void incrementalMenuAndDirectInventoryUpdatesKeepTheOwnersArmorGlint() {
        var menu = new ClientboundContainerSetSlotPacket(InventoryMenu.CONTAINER_ID, 7, 6, armor());
        var result = (ClientboundContainerSetSlotPacket) rewriter.rewrite(viewer, menu, lookup(rule()));
        assertPresentation(result.getItem(), true);
        var otherMenu = new ClientboundContainerSetSlotPacket(2, 7, 6, armor());
        assertPresentation(((ClientboundContainerSetSlotPacket) rewriter.rewrite(viewer, otherMenu,
            lookup(rule()))).getItem(), false);
        int chestSlot = Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet().stream()
            .filter(entry -> entry.getValue() == EquipmentSlot.CHEST).findFirst().orElseThrow().getIntKey();
        var direct = new ClientboundSetPlayerInventoryPacket(chestSlot, armor());
        var directResult = (ClientboundSetPlayerInventoryPacket) rewriter.rewrite(viewer, direct, lookup(rule()));
        assertPresentation(directResult.contents(), true);
        var hotbar = new ClientboundSetPlayerInventoryPacket(0, armor());
        assertPresentation(((ClientboundSetPlayerInventoryPacket) rewriter.rewrite(viewer, hotbar,
            lookup(rule()))).contents(), false);
    }

    @Test
    void cursorAndDroppedArmorNeverUseTheWornOverride() {
        var cursor = new ClientboundSetCursorItemPacket(armor());
        assertPresentation(((ClientboundSetCursorItemPacket) rewriter.rewrite(viewer, cursor, lookup(rule())))
            .contents(), false);
        var metadata = new ClientboundSetEntityDataPacket(42,
            List.of(new SynchedEntityData.DataValue<>(8, EntityDataSerializers.ITEM_STACK, armor())));
        var result = (ClientboundSetEntityDataPacket) rewriter.rewrite(viewer, metadata, lookup(rule()));
        assertPresentation((ItemStack) result.packedItems().getFirst().value(), false);
    }

    @Test
    void bundlesKeepArmorContextAndExplicitFalseOrMissingOverridesPreserveLegacyBehavior() {
        var equipment = new ClientboundSetEquipmentPacket(42, List.of(Pair.of(EquipmentSlot.CHEST, armor())));
        var bundle = new ClientboundBundlePacket(List.of(equipment));
        var result = (ClientboundBundlePacket) rewriter.rewrite(viewer, bundle, lookup(rule()));
        assertPresentation(((ClientboundSetEquipmentPacket) result.subPackets().iterator().next())
            .getSlots().getFirst().getSecond(), true);
        for (var disabled : List.of(rule().toBuilder().wornEnchantmentGlint(false).build(),
            rule().toBuilder().wornEnchantmentGlint(null).build())) {
            var rendered = (ClientboundSetEquipmentPacket) rewriter.rewrite(viewer, equipment, lookup(disabled));
            assertPresentation(rendered.getSlots().getFirst().getSecond(), false);
        }
        assertSame(equipment, rewriter.rewrite(viewer, equipment, lookup(null)));
    }

    @Test
    void predictedEquipAndUnequipClicksAcceptBothValidPresentationsButRejectChangedGameplayData() {
        var original = armor();
        for (boolean worn : List.of(false, true)) {
            var projected = transformer.rewrite(viewer, original, lookup(rule()), worn);
            HashedStack clientHash = (candidate, hasher) -> ItemStack.isSameItemSameComponents(projected, candidate);
            var changes = new Int2ObjectOpenHashMap<HashedStack>();
            changes.put(6, clientHash);
            var click = new ServerboundContainerClickPacket(0, 7, (short) 6, (byte) 0, ContainerInput.PICKUP,
                changes, clientHash);
            var sanitized = (ServerboundContainerClickPacket) rewriter.sanitize(viewer, click, lookup(rule()));
            assertTrue(sanitized.carriedItem().matches(original, null));
            assertTrue(sanitized.changedSlots().get(6).matches(original, null));
            var damaged = original.copy();
            damaged.set(DataComponents.DAMAGE, 99);
            assertFalse(sanitized.carriedItem().matches(damaged, null));
            var restored = transformer.sanitize(projected, rule());
            assertTrue(ItemStack.isSameItemSameComponents(original, restored));
            assertEquals(7, restored.get(DataComponents.DAMAGE));
        }
    }

    @Test
    void creativeSanitationRestoresCanonicalGlintEvenWhenOnlyTheWornOverrideWasSet() {
        var original = armor();
        original.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        var wornRule = FakeItemMetaRule.builder().wornEnchantmentGlint(true)
            .persistentEnchantmentGlint(false).build();
        var projected = transformer.rewrite(viewer, original, lookup(wornRule), true);
        assertTrue(projected.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        assertTrue(ItemStack.isSameItemSameComponents(original, transformer.sanitize(projected, wornRule)));
    }

    private static ItemStack armor() {
        var stack = new ItemStack(Items.IRON_CHESTPLATE);
        stack.set(DataComponents.ITEM_MODEL, Identifier.parse("isles:silver-violet-chestplate"));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.DAMAGE, 7);
        return stack;
    }

    private static FakeItemMetaRule rule() {
        return FakeItemMetaRule.builder()
            .itemModel(new NamespacedKey("isles", "rarity_glint/epic/isles/silver-violet-chestplate"))
            .persistentItemModel(new NamespacedKey("isles", "silver-violet-chestplate"))
            .enchantmentGlint(false).wornEnchantmentGlint(true).persistentEnchantmentGlint(true).build();
    }

    private static FakeItemMetaLookup lookup(final FakeItemMetaRule rule) {
        return new FakeItemMetaLookup() {
            @Override
            public Optional<FakeItemMetaRule> find(final UUID viewerId,
                                                   final org.bukkit.inventory.ItemStack stack) {
                return Optional.ofNullable(rule);
            }

            @Override
            public boolean hasAny(final UUID viewerId) {
                return rule != null;
            }
        };
    }

    private static void assertPresentation(final ItemStack stack, final boolean worn) {
        assertEquals(Identifier.parse("isles:rarity_glint/epic/isles/silver-violet-chestplate"),
            stack.get(DataComponents.ITEM_MODEL));
        assertEquals(worn, stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
    }
}
