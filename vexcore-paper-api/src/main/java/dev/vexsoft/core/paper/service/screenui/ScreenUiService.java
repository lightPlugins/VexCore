package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.DialoguePanel;
import dev.vexsoft.core.paper.screenui.DialoguePanelLayout;
import dev.vexsoft.core.paper.screenui.PreparedDialogue;
import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Shared bossbar-backed UI. Resolve from the requesting plugin's scoped registry. */
public interface ScreenUiService extends VexService {
  ScreenUi open(Player player, String id);
  Optional<ScreenUi> find(Player player, String id);
  void validateDialogueLayout(DialoguePanelLayout layout);
  PreparedDialogue prepareDialogue(Component speaker, List<Component> paragraphs, DialoguePanelLayout layout);
  DialoguePanel openDialogue(Player player, String id, PreparedDialogue dialogue);
}
