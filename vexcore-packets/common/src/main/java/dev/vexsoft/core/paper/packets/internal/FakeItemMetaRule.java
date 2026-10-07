package dev.vexsoft.core.paper.packets.internal;

import dev.vexsoft.core.paper.packets.item.FakeItemLoreMode;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;

@Value
@Builder(toBuilder = true)
public class FakeItemMetaRule {

    Component displayName;
    NamespacedKey itemModel;
    /** Configured server model to restore when Creative sends the rendered stack back. */
    NamespacedKey persistentItemModel;
    NamespacedKey tooltipStyle;
    List<Component> lore;
    FakeItemLoreMode loreMode;
    boolean hideVanillaDetails;
}
