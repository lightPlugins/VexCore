package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.DialoguePanelLayout;
import dev.vexsoft.core.paper.screenui.PreparedDialogue;
import java.util.List;
import net.kyori.adventure.text.Component;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Internal cross-owner coordinator. */
public interface ScreenUiCoordinatorService extends VexService {
  ScreenUi open(ServiceOwner owner, Player player, String id);
  Optional<ScreenUi> find(ServiceOwner owner, Player player, String id);
  void closeOwner(ServiceOwner owner);
  void discard(Player player);
  void validateDialogueLayout(DialoguePanelLayout layout);
  PreparedDialogue prepareDialogue(Component speaker, List<Component> paragraphs, DialoguePanelLayout layout);
}
