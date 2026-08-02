package dev.vexsoft.core.configuration;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ConfigurateConfigurationSection implements ConfigurationSection {
  protected final CommentedConfigurationNode node;

  ConfigurateConfigurationSection(CommentedConfigurationNode node) {
    this.node = node;
  }

  @Override public boolean contains(String path) { return !resolve(path).virtual(); }
  @Override public Object get(String path) { return resolve(path).raw(); }
  @Override public String getString(String path) { return resolve(path).getString(); }
  @Override public String getString(String path, String value) { return resolve(path).getString(value); }
  @Override public int getInt(String path, int value) { return resolve(path).getInt(value); }
  @Override public long getLong(String path, long value) { return resolve(path).getLong(value); }
  @Override public double getDouble(String path, double value) { return resolve(path).getDouble(value); }
  @Override public boolean getBoolean(String path, boolean value) { return resolve(path).getBoolean(value); }

  @Override
  public List<String> getStringList(String path) {
    List<String> values = new ArrayList<>();
    for (ConfigurationNode child : resolve(path).childrenList()) {
      String value = child.getString();
      if (value != null) {
        values.add(value);
      }
    }
    return List.copyOf(values);
  }

  @Override
  public ConfigurationSection getSection(String path) {
    CommentedConfigurationNode section = resolve(path);
    return section.virtual() ? null : new ConfigurateConfigurationSection(section);
  }

  @Override
  public Set<String> getKeys(boolean deep) {
    Set<String> result = new LinkedHashSet<>();
    collectKeys(node, "", deep, result);
    return Set.copyOf(result);
  }

  @Override
  public Map<String, Object> getValues(boolean deep) {
    Map<String, Object> result = new LinkedHashMap<>();
    collectValues(node, "", deep, result);
    return Map.copyOf(result);
  }

  @Override public void set(String path, Object value) { resolve(path).raw(value); }

  protected CommentedConfigurationNode resolve(String path) {
    if (path == null || path.isBlank()) {
      return node;
    }
    return node.node((Object[]) path.split("\\."));
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
