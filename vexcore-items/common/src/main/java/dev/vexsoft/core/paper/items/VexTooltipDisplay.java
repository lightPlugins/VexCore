package dev.vexsoft.core.paper.items;

import java.util.Set;

/** Hides selected component details while retaining custom name and lore. */
public record VexTooltipDisplay(boolean hideTooltip, Set<VexComponentKey> hiddenComponents) {

    /** Copies and validates the set of hidden tooltip components. */
    public VexTooltipDisplay {
        hiddenComponents = Set.copyOf(hiddenComponents);

        for (VexComponentKey key : hiddenComponents) {
            if (!Set.of(
                VexComponentKey.TRIM,
                VexComponentKey.DYED_COLOR,
                VexComponentKey.UNBREAKABLE,
                VexComponentKey.ATTRIBUTE_MODIFIERS,
                VexComponentKey.ENCHANTMENTS,
                VexComponentKey.DAMAGE
            ).contains(key)) {
                throw new IllegalArgumentException("Unsupported hidden tooltip component: " + key);
            }
        }
    }
}
