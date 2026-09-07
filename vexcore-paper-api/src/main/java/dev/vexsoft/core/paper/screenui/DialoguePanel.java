package dev.vexsoft.core.paper.screenui;

import net.kyori.adventure.text.Component;

/** A presentation handle; the caller owns timing and completion semantics. */
public interface DialoguePanel extends AutoCloseable {
  /** Replaces the interaction hint displayed below the dialogue. */
  void setHint(Component hint);

  /** Displays the selected page with the requested number of visible code points. */
  void show(int page, int visibleCodePoints);

  /** Returns whether this presentation has been retired. */
  boolean isClosed();

  @Override
  void close();
}
