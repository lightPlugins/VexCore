package dev.vexsoft.core.paper.screenui;

import net.kyori.adventure.text.Component;

/** A presentation handle; the caller owns timing and completion semantics. */
public interface DialoguePanel extends AutoCloseable {
  void setHint(Component hint);
  void show(int page, int visibleCodePoints);
  boolean isClosed();
  @Override void close();
}
