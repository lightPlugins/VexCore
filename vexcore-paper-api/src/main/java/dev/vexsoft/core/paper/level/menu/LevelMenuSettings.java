package dev.vexsoft.core.paper.level.menu;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.paper.inventory.page.PageBounds;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.key.Key;

/** Technical, non-localized settings for a reusable snaked level menu. */
public record LevelMenuSettings(
    int inventorySize,
    PageBounds bounds,
    Map<LevelMenuState, Key> stateModels
) {

  /** Exact default visual sequence for level 1 through level 16. */
  public static final List<Integer> DEFAULT_SNAKE_SLOTS = List.of(
      10, 19, 28, 29, 30, 21, 12, 13, 14, 23, 32, 33, 34, 25, 16, 17
  );

  /** Validates menu bounds and copies state models. */
  public LevelMenuSettings {
    if (inventorySize < 9 || inventorySize > 54 || inventorySize % 9 != 0) {
      throw new IllegalArgumentException("inventorySize must be a multiple of nine from 9 to 54");
    }
    Objects.requireNonNull(bounds, "bounds");
    if (bounds.getSlots().stream().anyMatch(slot -> slot >= inventorySize)) {
      throw new IllegalArgumentException("level slots exceed the configured inventory size");
    }
    EnumMap<LevelMenuState, Key> checked = new EnumMap<>(LevelMenuState.class);
    checked.putAll(Objects.requireNonNull(stateModels, "stateModels"));
    for (LevelMenuState state : LevelMenuState.values()) {
      Objects.requireNonNull(checked.get(state), "Missing item model for state " + state);
    }
    stateModels = Map.copyOf(checked);
  }

  /** Creates the bundled 45-slot snake layout and neutral vanilla models. */
  public static LevelMenuSettings defaults() {
    EnumMap<LevelMenuState, Key> models = new EnumMap<>(LevelMenuState.class);
    models.put(LevelMenuState.CLAIMED, Key.key("minecraft:lime_dye"));
    models.put(LevelMenuState.CLAIMABLE, Key.key("minecraft:yellow_dye"));
    models.put(LevelMenuState.REQUIREMENTS_NOT_MET, Key.key("minecraft:orange_dye"));
    models.put(LevelMenuState.COSTS_NOT_AFFORDABLE, Key.key("minecraft:red_dye"));
    models.put(LevelMenuState.LOCKED, Key.key("minecraft:gray_dye"));
    return new LevelMenuSettings(45, PageBounds.ofSlots(DEFAULT_SNAKE_SLOTS), models);
  }

  /** Parses technical menu settings; visible strings intentionally belong in language files. */
  public static LevelMenuSettings compile(final ConfigurationSection root) {
    Objects.requireNonNull(root, "root");
    ConfigurationSection menu = root.getSection("menu");
    if (menu == null) {
      return defaults();
    }
    LevelMenuSettings defaults = defaults();
    int size = menu.getInt("size", defaults.inventorySize());
    List<Integer> slots = parseSlots(menu.get("slots"));
    if (slots.isEmpty()) {
      slots = DEFAULT_SNAKE_SLOTS;
    }
    EnumMap<LevelMenuState, Key> models = new EnumMap<>(LevelMenuState.class);
    for (LevelMenuState state : LevelMenuState.values()) {
      String path = "states." + configName(state) + ".item-model";
      String configured = menu.getString(path, defaults.stateModels().get(state).asString());
      models.put(state, Key.key(configured));
    }
    return new LevelMenuSettings(size, PageBounds.ofSlots(slots), models);
  }

  private static List<Integer> parseSlots(final Object raw) {
    if (!(raw instanceof List<?> values)) {
      return List.of();
    }
    List<Integer> slots = new ArrayList<>();
    for (Object value : values) {
      if (!(value instanceof Number number)) {
        throw new IllegalArgumentException("Every menu slot must be a number");
      }
      slots.add(number.intValue());
    }
    return List.copyOf(slots);
  }

  private static String configName(final LevelMenuState state) {
    return state.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
