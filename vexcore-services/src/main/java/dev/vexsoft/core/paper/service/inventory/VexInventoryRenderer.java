package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import dev.vexsoft.core.paper.inventory.InventoryView;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class VexInventoryRenderer {

    Inventory render(
        final InventoryContext context,
        final InventoryView view,
        final VexInventoryHolder holder,
        final Map<Integer, InventoryElement> elements,
        final NamespacedKey defaultTooltipStyle
    ) {
        Inventory inventory =
            Bukkit.createInventory(Objects.requireNonNull(holder, "holder"), view.getSize(), view.getTitle(context));

        holder.attach(inventory);
        holder.setInventoryKey(view.getKey());
        renderInto(context, inventory, elements, defaultTooltipStyle);

        return inventory;
    }

    void renderInto(
        final InventoryContext context,
        final Inventory inventory,
        final Map<Integer, InventoryElement> elements,
        final NamespacedKey defaultTooltipStyle
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(elements, "elements");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            InventoryElement element = elements.get(slot);
            ItemStack rendered = element == null ? null : element.render(context);
            ItemStack item = rendered == null ? null : rendered.clone();

            applyDefaultTooltipStyle(item, defaultTooltipStyle);
            // Keep unchanged stacks in place; clearing and refilling creates needless slot updates.
            if (!Objects.equals(inventory.getItem(slot), item)) {
                inventory.setItem(slot, item);
            }
        }
    }

    static void applyDefaultTooltipStyle(final ItemStack item, final NamespacedKey defaultTooltipStyle) {
        if (item == null || defaultTooltipStyle == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey tooltipStyle = tooltipStyle(meta.getTooltipStyle(), defaultTooltipStyle, meta.isHideTooltip());

        if (Objects.equals(meta.getTooltipStyle(), tooltipStyle)) {
            return;
        }

        meta.setTooltipStyle(tooltipStyle);
        item.setItemMeta(meta);
    }

    static NamespacedKey tooltipStyle(
        final NamespacedKey configured,
        final NamespacedKey fallback,
        final boolean hidden
    ) {
        return configured == null && !hidden ? fallback : configured;
    }
}
