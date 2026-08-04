package dev.vexsoft.core.packets.internal;

import dev.vexsoft.core.packets.item.FakeItemLoreMode;
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
  List<Component> lore;
  FakeItemLoreMode loreMode;
}
