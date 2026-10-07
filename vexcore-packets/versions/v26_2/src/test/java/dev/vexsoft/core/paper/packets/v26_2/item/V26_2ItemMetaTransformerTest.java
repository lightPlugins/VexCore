package dev.vexsoft.core.paper.packets.v26_2.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import dev.vexsoft.core.paper.packets.internal.FakeItemMetaRule;
import dev.vexsoft.core.paper.packets.item.FakeItemLoreMode;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Reproduces Creative returning a rendered item and separates persistent models from viewer overrides. */
public final class V26_2ItemMetaTransformerTest {

    private final V26_2ItemMetaTransformer transformer = new V26_2ItemMetaTransformer();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void creativePetKeepsItsRealModelWhileLocalizedTextIsRemoved() {
        var model = new NamespacedKey("isles", "arkie-tumblebud-item");
        var rendered = new ItemStack(Holder.direct(Items.NAME_TAG, DataComponentMap.EMPTY));
        rendered.set(DataComponents.ITEM_MODEL, Identifier.parse(model.asString()));
        rendered.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Tumblebud +5"));
        rendered.set(DataComponents.LORE, new ItemLore(List.of(net.minecraft.network.chat.Component.literal("Boni"))));
        rendered.set(DataComponents.MAX_STACK_SIZE, 1);
        var identity = new CompoundTag();
        identity.putString("pet_id", "ca7a8f2d-a1f5-4083-9d17-0ef6a87e88a7");
        identity.putString("arkie_rarity", "epic");
        identity.putInt("upgrade_level", 5);
        rendered.set(DataComponents.CUSTOM_DATA, CustomData.of(identity));
        var rule = FakeItemMetaRule.builder().itemModel(model).persistentItemModel(model)
            .displayName(net.kyori.adventure.text.Component.text("Tumblebud +5"))
            .lore(List.of(net.kyori.adventure.text.Component.text("Boni")))
            .loreMode(FakeItemLoreMode.REPLACE).build();

        var restored = transformer.sanitize(rendered, rule);

        assertNotSame(rendered, restored);
        assertEquals(Identifier.parse(model.asString()), restored.get(DataComponents.ITEM_MODEL));
        assertEquals(1, restored.get(DataComponents.MAX_STACK_SIZE));
        assertEquals(rendered.get(DataComponents.CUSTOM_DATA), restored.get(DataComponents.CUSTOM_DATA));
        assertNull(restored.get(DataComponents.CUSTOM_NAME));
        assertNull(restored.get(DataComponents.LORE));
        assertEquals("Tumblebud +5", rendered.get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void viewerOnlyModelIsRemovedButAConfiguredModelIsRestoredInstead() {
        var rendered = new ItemStack(Holder.direct(Items.NAME_TAG, DataComponentMap.EMPTY));
        var preview = new NamespacedKey("isles", "preview");
        var persistent = new NamespacedKey("isles", "arkie-tumblebud-item");
        rendered.set(DataComponents.ITEM_MODEL, Identifier.parse(preview.asString()));
        var transientRule = FakeItemMetaRule.builder().itemModel(preview).build();

        assertNull(transformer.sanitize(rendered, transientRule).get(DataComponents.ITEM_MODEL));
        assertEquals(Identifier.parse(persistent.asString()), transformer.sanitize(rendered,
            transientRule.toBuilder().persistentItemModel(persistent).build()).get(DataComponents.ITEM_MODEL));
        assertEquals(Identifier.parse(preview.asString()), rendered.get(DataComponents.ITEM_MODEL));
    }
}
