package dev.vexsoft.core.paper.packet.service;

import dev.vexsoft.core.packets.internal.FakeItemMetaRule;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.item.FakeItemLoreMode;
import dev.vexsoft.core.packets.service.FakeItemMetaService;
import dev.vexsoft.core.paper.packet.item.FakeItemMetaStoreService;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

@Dependencies(FakeItemMetaStoreService.class)
public final class VexFakeItemMetaService implements FakeItemMetaService, AutoCloseable {

  private final ServiceOwner owner;
  private final FakeItemMetaStoreService store;

  public VexFakeItemMetaService(final VexServiceRegistry services) {
    this.owner = services.getOwner();
    this.store = services.require(FakeItemMetaStoreService.class);
  }

  @Override
  public void setLore(
      final NamespacedKey itemIdKey,
      final String itemId,
      final List<Component> lore,
      final FakeItemLoreMode mode
  ) {
    lore(null, itemIdKey, itemId, lore, mode);
  }

  @Override
  public void setLore(
      final Player viewer,
      final NamespacedKey itemIdKey,
      final String itemId,
      final List<Component> lore,
      final FakeItemLoreMode mode
  ) {
    lore(viewer.getUniqueId(), itemIdKey, itemId, lore, mode);
  }

  @Override
  public void setDisplayName(
      final NamespacedKey itemIdKey,
      final String itemId,
      final Component displayName
  ) {
    update(null, itemIdKey, itemId, rule -> rule.toBuilder().displayName(displayName).build());
  }

  @Override
  public void setDisplayName(
      final Player viewer,
      final NamespacedKey itemIdKey,
      final String itemId,
      final Component displayName
  ) {
    update(viewer.getUniqueId(), itemIdKey, itemId,
        rule -> rule.toBuilder().displayName(displayName).build());
  }

  @Override
  public void setItemModel(
      final NamespacedKey itemIdKey,
      final String itemId,
      final NamespacedKey itemModel
  ) {
    update(null, itemIdKey, itemId, rule -> rule.toBuilder().itemModel(itemModel).build());
  }

  @Override
  public void setItemModel(
      final Player viewer,
      final NamespacedKey itemIdKey,
      final String itemId,
      final NamespacedKey itemModel
  ) {
    update(viewer.getUniqueId(), itemIdKey, itemId,
        rule -> rule.toBuilder().itemModel(itemModel).build());
  }

  @Override
  public void clearLore(final NamespacedKey itemIdKey, final String itemId) {
    clearLore((UUID) null, itemIdKey, itemId);
  }

  @Override
  public void clearLore(
      final Player viewer,
      final NamespacedKey itemIdKey,
      final String itemId
  ) {
    clearLore(viewer.getUniqueId(), itemIdKey, itemId);
  }

  @Override
  public void clearDisplayName(final NamespacedKey itemIdKey, final String itemId) {
    clearDisplayName((UUID) null, itemIdKey, itemId);
  }

  @Override
  public void clearDisplayName(
      final Player viewer,
      final NamespacedKey itemIdKey,
      final String itemId
  ) {
    clearDisplayName(viewer.getUniqueId(), itemIdKey, itemId);
  }

  @Override
  public void clearItemModel(final NamespacedKey itemIdKey, final String itemId) {
    clearItemModel((UUID) null, itemIdKey, itemId);
  }

  @Override
  public void clearItemModel(
      final Player viewer,
      final NamespacedKey itemIdKey,
      final String itemId
  ) {
    clearItemModel(viewer.getUniqueId(), itemIdKey, itemId);
  }

  @Override
  public void clearAll() {
    store.clearOwned(owner);
    refreshAll();
  }

  @Override
  public void clearAll(final Player viewer) {
    store.clearOwned(owner, viewer.getUniqueId());
    refresh(viewer);
  }

  @Override
  public void refresh(final Player viewer) {
    viewer.updateInventory();
  }

  @Override
  public void refreshAll() {
    Bukkit.getOnlinePlayers().forEach(this::refresh);
  }

  @Override
  public void close() {
    store.clearOwned(owner);
    refreshAll();
  }

  private void lore(
      final UUID viewerId,
      final NamespacedKey itemIdKey,
      final String itemId,
      final List<Component> lore,
      final FakeItemLoreMode mode
  ) {
    update(viewerId, itemIdKey, itemId,
        rule -> rule.toBuilder().lore(List.copyOf(lore)).loreMode(mode).build());
  }

  private void clearLore(
      final UUID viewerId,
      final NamespacedKey itemIdKey,
      final String itemId
  ) {
    update(viewerId, itemIdKey, itemId,
        rule -> rule.toBuilder().lore(null).loreMode(null).build());
  }

  private void clearDisplayName(
      final UUID viewerId,
      final NamespacedKey itemIdKey,
      final String itemId
  ) {
    update(viewerId, itemIdKey, itemId,
        rule -> rule.toBuilder().displayName(null).build());
  }

  private void clearItemModel(
      final UUID viewerId,
      final NamespacedKey itemIdKey,
      final String itemId
  ) {
    update(viewerId, itemIdKey, itemId,
        rule -> rule.toBuilder().itemModel(null).build());
  }

  private void update(
      final UUID viewerId,
      final NamespacedKey itemIdKey,
      final String itemId,
      final UnaryOperator<FakeItemMetaRule> updater
  ) {
    store.update(owner, viewerId, itemIdKey, itemId, updater);
  }
}
