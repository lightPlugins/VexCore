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
import net.kyori.adventure.key.Key;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

public final class V26_2ItemMetaTransformer {

    public ItemStack rewrite(final UUID viewerId, final ItemStack item, final FakeItemMetaLookup lookup) {
        Optional<FakeItemMetaRule> result = find(viewerId, item, lookup);

        if (result.isEmpty()) {
            return item;
        }

        ItemStack copy = item.copy();

        apply(copy, result.get());

        if (result.get().getTooltipStyle() != null || result.get().isHideVanillaDetails()) {
            org.bukkit.inventory.ItemStack bukkit = CraftItemStack.asBukkitCopy(copy);
            if (result.get().getTooltipStyle() != null) {
                bukkit.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key(result.get().getTooltipStyle().asString()));
            }
            if (result.get().isHideVanillaDetails()) {
                hideVanillaDetails(bukkit);
            }
            return CraftItemStack.asNMSCopy(bukkit);
        }

        return copy;
    }

    public ItemStack sanitize(final UUID viewerId, final ItemStack item, final FakeItemMetaLookup lookup) {
        Optional<FakeItemMetaRule> result = find(viewerId, item, lookup);

        if (result.isEmpty()) {
            return item;
        }

        return sanitize(item, result.get());
    }

    ItemStack sanitize(final ItemStack item, final FakeItemMetaRule rule) {
        ItemStack copy = item.copy();

        if (rule.getDisplayName() != null) {
            copy.remove(DataComponents.CUSTOM_NAME);
        }

        if (rule.getItemModel() != null) {
            // Client presentation may override a real item model; only the transient override is disposable.
            if (rule.getPersistentItemModel() == null) {
                copy.remove(DataComponents.ITEM_MODEL);
            } else {
                copy.set(DataComponents.ITEM_MODEL, Identifier.parse(rule.getPersistentItemModel().asString()));
            }
        }

        if (rule.getLore() != null) {
            copy.remove(DataComponents.LORE);
        }

        if (rule.getTooltipStyle() != null) {
            copy.remove(DataComponents.TOOLTIP_STYLE);
        }

        if (rule.isHideVanillaDetails()) {
            copy.remove(DataComponents.TOOLTIP_DISPLAY);
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

    private static void apply(final ItemStack item, final FakeItemMetaRule rule) {
        if (rule.getDisplayName() != null) {
            item.set(DataComponents.CUSTOM_NAME, PaperAdventure.asVanilla(rule.getDisplayName()));
        }

        if (rule.getItemModel() != null) {
            item.set(DataComponents.ITEM_MODEL, Identifier.parse(rule.getItemModel().asString()));
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
