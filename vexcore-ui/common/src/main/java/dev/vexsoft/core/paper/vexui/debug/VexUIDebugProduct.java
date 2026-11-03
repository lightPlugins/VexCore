package dev.vexsoft.core.paper.vexui.debug;

import dev.vexsoft.core.number.WholeAmount;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;

/** Vanilla test product with an illustrative price, never charged to a wallet. */
public record VexUIDebugProduct(Material material, String category, WholeAmount price, int maximumStackSize) {

    public VexUIDebugProduct {
        if (material == null || category == null || price == null || maximumStackSize < 1 || maximumStackSize > 99) {
            throw new IllegalArgumentException("Invalid VexUI debug product");
        }
    }

    public String id() {
        return material.name().toLowerCase(Locale.ROOT);
    }

    public Key sprite() {
        return Key.key("minecraft", "item/" + id());
    }
}
