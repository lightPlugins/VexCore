package dev.vexsoft.core.paper.service.packets.item;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaLookup;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaRule;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.bukkit.NamespacedKey;

/**
 * Stores fake item rules from every plugin for the packet rewriter
 */
public interface FakeItemMetaStoreService extends VexService, FakeItemMetaLookup {

  /** Updates a global or viewer-specific fake item rule */
  void update(
      ServiceOwner owner,
      UUID viewerId,
      NamespacedKey itemIdKey,
      String itemId,
      UnaryOperator<FakeItemMetaRule> updater
  );

  /** Removes every fake item rule owned by one plugin */
  void clearOwned(ServiceOwner owner);

  /** Removes one plugin's fake item rules for one viewer */
  void clearOwned(ServiceOwner owner, UUID viewerId);
}
