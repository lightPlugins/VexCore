package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.screenui.DialoguePanel;
import dev.vexsoft.core.paper.screenui.DialoguePanelLayout;
import dev.vexsoft.core.paper.screenui.PreparedDialogue;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Shared bossbar-backed UI. Resolve from the requesting plugin's scoped registry. */
public interface ScreenUiService extends VexService {
  /** Opens a named viewer-specific screen. */
  ScreenUi open(Player player, String id);

  /** Finds an existing screen for the viewer without creating it. */
  Optional<ScreenUi> find(Player player, String id);

  /** Checks that the dialogue layout can be rendered by the active adapter. */
  void validateDialogueLayout(DialoguePanelLayout layout);

  /** Wraps paragraphs into immutable pages for the requested speaker and layout. */
  PreparedDialogue prepareDialogue(
      Component speaker, List<Component> paragraphs, DialoguePanelLayout layout);

  /** Opens the prepared dialogue for the viewer. */
  DialoguePanel openDialogue(Player player, String id, PreparedDialogue dialogue);
}
