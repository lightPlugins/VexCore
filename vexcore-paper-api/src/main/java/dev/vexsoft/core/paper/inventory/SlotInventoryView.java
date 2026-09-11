package dev.vexsoft.core.paper.inventory;

import org.bukkit.event.inventory.ClickType;

/**
 * Opt-in routing for menus with controls and externally stored item slots.
 *
 * <p>The framework keeps normal player-inventory movement available and dispatches cursor
 * transfers only to CursorInventoryElement. Menu render stacks and placeholders never become
 * real inventory items. Existing MutableInventoryView remains the raw-event extension point.</p>
 */
public interface SlotInventoryView extends InventoryView {

    /** Controls and cursor slots accept only left-click by default. */
    default boolean acceptsMenuClick(ClickType click) {
        return click == ClickType.LEFT;
    }
}
