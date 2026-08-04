package dev.vexsoft.core.paper.packet.item;

import dev.vexsoft.core.api.service.ServiceOwner;
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
