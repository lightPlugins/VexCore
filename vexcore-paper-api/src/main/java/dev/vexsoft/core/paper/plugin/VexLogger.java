package dev.vexsoft.core.paper.plugin;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

public final class VexLogger extends Logger {

  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  @Getter
  @Setter
  @NonNull
  private volatile String prefix;

  public VexLogger(final String name, final String prefix) {
    super(name, null);
    this.prefix = Objects.requireNonNull(prefix, "prefix");
    setUseParentHandlers(false);
  }

  @Override
  public void log(final LogRecord record) {
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
