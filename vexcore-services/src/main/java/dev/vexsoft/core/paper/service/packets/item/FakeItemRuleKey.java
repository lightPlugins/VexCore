package dev.vexsoft.core.paper.service.packets.item;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import java.util.UUID;
import lombok.Value;
import org.bukkit.NamespacedKey;

@Value
public class FakeItemRuleKey {
  ServiceOwner owner;
  UUID viewerId;
  NamespacedKey itemIdKey;
  String itemId;
}
