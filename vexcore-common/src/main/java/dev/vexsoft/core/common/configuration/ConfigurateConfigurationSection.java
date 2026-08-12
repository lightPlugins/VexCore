package dev.vexsoft.core.common.configuration;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class ConfigurateConfigurationSection implements ConfigurationSection {
  protected final CommentedConfigurationNode node;
  protected final ReentrantReadWriteLock lock;

  public ConfigurateConfigurationSection(CommentedConfigurationNode node) {
    this(node, new ReentrantReadWriteLock());
  }

  /** Creates an independent section backed by an arbitrary Configurate-compatible value. */
  public static ConfigurateConfigurationSection from(final Object value) {
    CommentedConfigurationNode root = CommentedConfigurationNode.root();
    root.raw(value);
    return new ConfigurateConfigurationSection(root);
  }

  public ConfigurateConfigurationSection(
      CommentedConfigurationNode node,
      ReentrantReadWriteLock lock
  ) {
    this.node = node;
    this.lock = lock;
  }

  @Override public boolean contains(String path) { return read(() -> !resolve(path).virtual()); }
  @Override public Object get(String path) { return read(() -> resolve(path).raw()); }
  @Override public String getString(String path) { return read(() -> resolve(path).getString()); }
  @Override public String getString(String path, String value) { return read(() -> resolve(path).getString(value)); }
  @Override public int getInt(String path, int value) { return read(() -> resolve(path).getInt(value)); }
  @Override public long getLong(String path, long value) { return read(() -> resolve(path).getLong(value)); }
  @Override public double getDouble(String path, double value) { return read(() -> resolve(path).getDouble(value)); }
  @Override public boolean getBoolean(String path, boolean value) { return read(() -> resolve(path).getBoolean(value)); }

  @Override
  public List<String> getStringList(String path) {
    return read(() -> {
      List<String> values = new ArrayList<>();
      for (ConfigurationNode child : resolve(path).childrenList()) {
        String value = child.getString();
        if (value != null) {
          values.add(value);
        }
      }
      return List.copyOf(values);
    });
  }

  @Override
  public ConfigurationSection getSection(String path) {
    return read(() -> {
      CommentedConfigurationNode section = resolve(path);
      // Child sections share the same lock because they still point at the same YAML tree
      return section.virtual() ? null : new ConfigurateConfigurationSection(section, lock);
    });
  }

  @Override
  public Set<String> getKeys(boolean deep) {
    return read(() -> {
      Set<String> result = new LinkedHashSet<>();
      collectKeys(node, "", deep, result);
      return Set.copyOf(result);
    });
  }

  @Override
  public Map<String, Object> getValues(boolean deep) {
    return read(() -> {
      Map<String, Object> result = new LinkedHashMap<>();
      collectValues(node, "", deep, result);
      return Map.copyOf(result);
    });
  }

  @Override public void set(String path, Object value) { write(() -> resolve(path).raw(value)); }

  protected CommentedConfigurationNode resolve(String path) {
    if (path == null || path.isBlank()) {
      return node;
    }
    return node.node((Object[]) path.split("\\."));
  }

  protected <T> T read(Supplier<T> action) {
    lock.readLock().lock();
    try {
      return action.get();
    } finally {
      lock.readLock().unlock();
    }
  }

  protected void write(Runnable action) {
    lock.writeLock().lock();
    try {
      action.run();
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void collectKeys(CommentedConfigurationNode current, String prefix, boolean deep, Set<String> out) {
    for (Map.Entry<Object, ? extends ConfigurationNode> entry : current.childrenMap().entrySet()) {
      String path = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
      out.add(path);
      if (deep && entry.getValue() instanceof CommentedConfigurationNode child && child.isMap()) {
        collectKeys(child, path, true, out);
      }
    }
  }

  private void collectValues(CommentedConfigurationNode current, String prefix, boolean deep, Map<String, Object> out) {
    for (Map.Entry<Object, ? extends ConfigurationNode> entry : current.childrenMap().entrySet()) {
      String path = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
      ConfigurationNode child = entry.getValue();
      if (deep && child instanceof CommentedConfigurationNode section && section.isMap()) {
        collectValues(section, path, true, out);
      } else {
        out.put(path, child.raw());
      }
    }
  }
}
