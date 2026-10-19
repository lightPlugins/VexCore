package dev.vexsoft.core.paper.packets.v26_2.item;

import dev.vexsoft.core.paper.packets.internal.FakeItemMetaLookup;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaRule;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.adventure.PaperAdventure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.IntSupplier;
import net.kyori.adventure.key.Key;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.resources.ResourceKey;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

public final class V26_2ItemMetaTransformer {

    private final IntSupplier ticks;

    public V26_2ItemMetaTransformer() {
        this(Bukkit::getCurrentTick);
    }

    V26_2ItemMetaTransformer(final IntSupplier ticks) {
        this.ticks = ticks;
    }

    public ItemStack rewrite(final UUID viewerId, final ItemStack item, final FakeItemMetaLookup lookup) {
        return rewrite(viewerId, item, lookup, false);
    }

    public ItemStack sanitize(final UUID viewerId, final ItemStack item, final FakeItemMetaLookup lookup) {
        Optional<FakeItemMetaRule> result = find(viewerId, item, lookup);

        if (result.isEmpty()) {
            return item;
        }

        return sanitize(item, result.get());
    }

    ItemStack rewrite(final UUID viewerId, final ItemStack item, final FakeItemMetaLookup lookup, final boolean worn) {
        Optional<FakeItemMetaRule> result = find(viewerId, item, lookup);

        if (result.isEmpty()) {
            return item;
        }

        return rewrite(item, result.get(), worn, -1);
    }

    boolean matches(
        final UUID viewerId,
        final ItemStack item,
        final FakeItemMetaLookup lookup,
        final Predicate<ItemStack> predicate
    ) {
        var rule = find(viewerId, item, lookup);
        if (rule.isEmpty()) {
            return predicate.test(item);
        }
        if (predicate.test(rewrite(item, rule.get(), false, -1))) {
            return true;
        }
        var animation = rule.get().getEquipmentAnimation();
        int frames = animation == null ? 1 : animation.frames().size();
        // Predicted clicks can refer to any previously displayed frame; gameplay components still must match.
        for (int frame = 0; frame < frames; frame++) {
            if (predicate.test(rewrite(item, rule.get(), true, frame))) {
                return true;
            }
        }
        return false;
    }

    private ItemStack rewrite(final ItemStack item, final FakeItemMetaRule rule, final boolean worn, final int frame) {
        ItemStack copy = item.copy();

        apply(copy, rule, worn, frame);

        if (rule.getTooltipStyle() != null || rule.isHideVanillaDetails()) {
            org.bukkit.inventory.ItemStack bukkit = CraftItemStack.asBukkitCopy(copy);
            if (rule.getTooltipStyle() != null) {
                bukkit.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key(rule.getTooltipStyle().asString()));
            }
            if (rule.isHideVanillaDetails()) {
                hideVanillaDetails(bukkit);
            }
            return CraftItemStack.asNMSCopy(bukkit);
        }

        return copy;
    }

    ItemStack sanitize(final ItemStack item, final FakeItemMetaRule rule) {
        ItemStack copy = item.copy();

        // Removing default components creates negative patches that prevent stacking with ordinary items.
        if (rule.getDisplayName() != null) {
            copy.set(DataComponents.CUSTOM_NAME, copy.getPrototype().get(DataComponents.CUSTOM_NAME));
        }

        if (rule.getItemModel() != null) {
            // Client presentation may override a real item model; only the transient override is disposable.
            if (rule.getPersistentItemModel() == null) {
                copy.set(DataComponents.ITEM_MODEL, copy.getPrototype().get(DataComponents.ITEM_MODEL));
            } else {
                copy.set(DataComponents.ITEM_MODEL, Identifier.parse(rule.getPersistentItemModel().asString()));
            }
        }

        if (rule.getLore() != null) {
            copy.set(DataComponents.LORE, copy.getPrototype().get(DataComponents.LORE));
        }

        if (rule.getEnchantmentGlint() != null || rule.getWornEnchantmentGlint() != null) {
            copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, rule.getPersistentEnchantmentGlint() == null
                ? copy.getPrototype().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
                : rule.getPersistentEnchantmentGlint());
        }

        if (rule.getEquipmentAnimation() != null) {
            equipmentAsset(copy, rule.getEquipmentAnimation().original().asString());
        }

        if (rule.getTooltipStyle() != null) {
            copy.set(DataComponents.TOOLTIP_STYLE, copy.getPrototype().get(DataComponents.TOOLTIP_STYLE));
        }

        if (rule.isHideVanillaDetails()) {
            copy.set(DataComponents.TOOLTIP_DISPLAY, copy.getPrototype().get(DataComponents.TOOLTIP_DISPLAY));
        }

        return copy;
    }

    private static Optional<FakeItemMetaRule> find(
        final UUID viewerId,
        final ItemStack item,
        final FakeItemMetaLookup lookup
    ) {
        if (item == null || item.isEmpty() || !lookup.hasAny(viewerId)) {
            return Optional.empty();
        }

        return lookup.find(viewerId, CraftItemStack.asBukkitCopy(item));
    }

    private void apply(final ItemStack item, final FakeItemMetaRule rule, final boolean worn, final int frame) {
        if (rule.getDisplayName() != null) {
            item.set(DataComponents.CUSTOM_NAME, PaperAdventure.asVanilla(rule.getDisplayName()));
        }

        if (rule.getItemModel() != null) {
            item.set(DataComponents.ITEM_MODEL, Identifier.parse(rule.getItemModel().asString()));
        }

        Boolean glint = worn && rule.getWornEnchantmentGlint() != null
            ? rule.getWornEnchantmentGlint() : rule.getEnchantmentGlint();
        if (glint != null) {
            item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint);
        }

        if (worn && rule.getEquipmentAnimation() != null) {
            var animation = rule.getEquipmentAnimation();
            var asset = frame < 0 ? animation.at(ticks.getAsInt()) : animation.frames().get(frame);
            equipmentAsset(item, asset.asString());
        }

        if (rule.getLore() == null) {
            return;
        }

        List<Component> fakeLore = rule.getLore().stream().map(PaperAdventure::asVanilla).toList();
        List<Component> lore = switch (rule.getLoreMode()) {
            case REPLACE -> fakeLore;
            case PREPEND -> combine(fakeLore, existingLore(item));
            case APPEND -> combine(existingLore(item), fakeLore);
        };
        int size = Math.min(lore.size(), ItemLore.MAX_LINES);

        item.set(DataComponents.LORE, new ItemLore(List.copyOf(lore.subList(0, size))));
    }

    private static void equipmentAsset(final ItemStack item, final String asset) {
        Equippable value = item.get(DataComponents.EQUIPPABLE);
        if (value != null) {
            item.set(DataComponents.EQUIPPABLE, new Equippable(value.slot(), value.equipSound(),
                Optional.of(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.parse(asset))), value.cameraOverlay(),
                value.allowedEntities(), value.dispensable(), value.swappable(), value.damageOnHurt(),
                value.equipOnInteract(), value.canBeSheared(), value.shearingSound()));
        }
    }

    private static List<Component> existingLore(final ItemStack item) {
        ItemLore lore = item.get(DataComponents.LORE);

        return lore == null ? List.of() : lore.lines();
    }

    private static List<Component> combine(final List<Component> first, final List<Component> second) {
        List<Component> combined = new ArrayList<>(first.size() + second.size());

        combined.addAll(first);
        combined.addAll(second);

        return combined;
    }

    private static void hideVanillaDetails(final org.bukkit.inventory.ItemStack item) {
        TooltipDisplay existing = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        Set<DataComponentType> hidden = new HashSet<>(item.getDataTypes());
        hidden.remove(DataComponentTypes.CUSTOM_NAME);
        hidden.remove(DataComponentTypes.ITEM_NAME);
        hidden.remove(DataComponentTypes.LORE);
        hidden.remove(DataComponentTypes.TOOLTIP_DISPLAY);
        if (existing != null) {
            hidden.addAll(existing.hiddenComponents());
        }

        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
            .hideTooltip(false)
            .hiddenComponents(hidden)
            .build());
    }
}
