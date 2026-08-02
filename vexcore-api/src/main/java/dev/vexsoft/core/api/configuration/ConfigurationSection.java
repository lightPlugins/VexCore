package dev.vexsoft.core.api.configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides typed access to values inside a configuration tree
 */
public interface ConfigurationSection {
  /** Checks whether a value exists at the specified dotted path */
  public boolean contains(String path);

  /** Returns the raw value stored at the specified dotted path */
  public Object get(String path);

  /** Returns the string stored at the specified dotted path */
  public String getString(String path);

  /** Returns a string or the provided default when no string is present */
  public String getString(String path, String defaultValue);

  /** Returns an integer or the provided default when no integer is present */
  public int getInt(String path, int defaultValue);

  /** Returns a long or the provided default when no long is present */
  public long getLong(String path, long defaultValue);

  /** Returns a double or the provided default when no double is present */
  public double getDouble(String path, double defaultValue);

  /** Returns a boolean or the provided default when no boolean is present */
  public boolean getBoolean(String path, boolean defaultValue);

  /** Returns an immutable string list stored at the specified dotted path */
  public List<String> getStringList(String path);

  /** Returns the nested configuration section at the specified dotted path */
  public ConfigurationSection getSection(String path);

  /** Returns the keys in this section, optionally including nested keys */
  public Set<String> getKeys(boolean deep);

  /** Returns the values in this section, optionally including nested values */
  public Map<String, Object> getValues(boolean deep);

  /** Stores or replaces a value at the specified dotted path */
  public void set(String path, Object value);
}
