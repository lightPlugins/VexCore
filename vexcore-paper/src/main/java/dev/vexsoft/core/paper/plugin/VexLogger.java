package dev.vexsoft.core.paper.plugin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class VexLogger extends Logger {
  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private volatile String prefix;

  public VexLogger(String name, String prefix) {
    super(name, null);
    this.prefix = Objects.requireNonNull(prefix, "prefix");
    setUseParentHandlers(false);
  }

  public String prefix() { return prefix; }
  public void prefix(String prefix) { this.prefix = Objects.requireNonNull(prefix, "prefix"); }

  @Override
  public void log(LogRecord record) {
    if (record == null || !isLoggable(record.getLevel())) {
      return;
    }
    String color = record.getLevel().intValue() >= Level.SEVERE.intValue() ? "<red>"
        : record.getLevel().intValue() >= Level.WARNING.intValue() ? "<yellow>" : "<gray>";
    Bukkit.getConsoleSender().sendMessage(
        miniMessage.deserialize(prefix + color + Objects.toString(record.getMessage(), ""))
    );
    if (record.getThrown() != null) {
      record.getThrown().printStackTrace();
    }
  }
}
