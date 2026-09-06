package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.paper.screenui.DialoguePanel;
import dev.vexsoft.core.paper.screenui.PreparedDialogue;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.TextOverflow;
import dev.vexsoft.core.paper.screenui.TextureLayout;
import dev.vexsoft.core.paper.screenui.UiTexture;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

/** Presentation only: callers control reveal timing, sounds, input and completion. */
public final class VexDialoguePanel implements DialoguePanel {
  private final ScreenUi screen;
  private final PreparedDialogue dialogue;
  private int lastPage = -1;
  private int lastVisible = -1;

  public VexDialoguePanel(ScreenUi screen, PreparedDialogue dialogue) {
    this.screen = screen;
    this.dialogue = dialogue;
    screen.remove("hint");
    var layout = dialogue.layout();
    for (int i = 0; i < 4; i++) {
      screen.remove("line-" + i);
    }
    screen.textureBlock("panel", UiTexture.PANEL, TextureLayout.builder().anchor(layout.anchor())
        .x(layout.panelX()).y(layout.panelY()).layer(0).build());
    screen.textBlock("speaker", List.of(dialogue.speaker()), TextBlockLayout.builder().anchor(layout.anchor())
        .x(layout.textX()).y(layout.nameY()).maxWidth(layout.textWidth()).overflow(TextOverflow.REJECT).layer(10).build());
    for (int i = 0; i < layout.linesPerPage(); i++) {
      screen.textBlock("line-" + i, List.of(Component.empty()), TextBlockLayout.builder().anchor(layout.anchor())
          .x(layout.textX()).y(layout.bodyY() + i * (12 + layout.lineSpacing())).maxWidth(layout.textWidth())
          .overflow(TextOverflow.REJECT).layer(10).build());
    }
  }

  @Override public void setHint(Component hint) {
    var layout = dialogue.layout();
    screen.textBlock("hint", List.of(hint), TextBlockLayout.builder().anchor(layout.anchor())
        .x(layout.textX()).y(layout.hintY()).maxWidth(layout.textWidth()).overflow(TextOverflow.ELLIPSIS)
        .layer(10).build());
  }

  @Override public synchronized void show(int page, int visibleCodePoints) {
    if (screen.isClosed()) {
      throw new IllegalStateException("Dialogue panel is closed");
    }
    var content = dialogue.pages().get(page);
    int visible = Math.clamp(visibleCodePoints, 0, content.codePoints());
    if (page == lastPage && visible == lastVisible) {
      return;
    }
    int[] remaining = {visible};
    for (int i = 0; i < dialogue.layout().linesPerPage(); i++) {
      Component text = i < content.lines().size() ? reveal(content.lines().get(i), remaining) : Component.empty();
      screen.setLines("line-" + i, List.of(text));
    }
    lastPage = page;
    lastVisible = visible;
  }

  private static Component reveal(Component component, int[] remaining) {
    TextComponent text = (TextComponent) component;
    int take = Math.min(remaining[0], text.content().codePointCount(0, text.content().length()));
    remaining[0] -= take;
    Component result = text.content(text.content().substring(0, text.content().offsetByCodePoints(0, take)));
    List<Component> children = new ArrayList<>();
    for (Component child : component.children()) {
      if (remaining[0] == 0) {
        break;
      }
      children.add(reveal(child, remaining));
    }
    return result.children(children);
  }

  @Override public boolean isClosed() { return screen.isClosed(); }
  @Override public void close() { screen.close(); }
}
