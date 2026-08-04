package dev.vexsoft.core.paper.packet.item;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.packets.internal.FakeItemMetaLookup;
import dev.vexsoft.core.packets.internal.FakeItemMetaRule;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.bukkit.NamespacedKey;

/**
 * Stores fake item rules from every plugin for the packet rewriter
 */
public interface FakeItemMetaStoreService extends VexService, FakeItemMetaLookup {

  /** Updates a global or viewer-specific fake item rule */
  public void update(
      ServiceOwner owner,
      UUID viewerId,
      NamespacedKey itemIdKey,
      String itemId,
      UnaryOperator<FakeItemMetaRule> updater
  );

  /** Removes every fake item rule owned by one plugin */
  public void clearOwned(ServiceOwner owner);

  /** Removes one plugin's fake item rules for one viewer */
  public void clearOwned(ServiceOwner owner, UUID viewerId);
}
