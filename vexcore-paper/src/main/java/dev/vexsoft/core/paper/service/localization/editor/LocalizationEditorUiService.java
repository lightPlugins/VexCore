package dev.vexsoft.core.paper.service.localization.editor;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/** Opens the inventory-based localization editor. */
public interface LocalizationEditorUiService extends VexService {

  void open(Player player);
}
