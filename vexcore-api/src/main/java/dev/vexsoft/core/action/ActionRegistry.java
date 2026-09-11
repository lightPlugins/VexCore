package dev.vexsoft.core.action;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Owner-created compiler registry for typed actions. Execution is driven by the caller's scheduler. */
public final class ActionRegistry<C> {

    private final Map<String, Function<ConfigurationSection, CompiledAction<C>>> types = new LinkedHashMap<>();

    /** Registers a compiler for an action type and rejects duplicate type names. */
    public void register(String type, Function<ConfigurationSection, CompiledAction<C>> compiler) {
        if (types.putIfAbsent(type, compiler) != null) {
            throw new IllegalArgumentException("Duplicate action " + type);
        }
    }

    /** Compiles a configured action using its registered type compiler. */
    public CompiledAction<C> compile(ConfigurationSection section) {
        String type = section.getString("type", "");
        var compiler = types.get(type);

        if (compiler == null) {
            throw new IllegalArgumentException("Unknown action type " + type);
        }

        return compiler.apply(section);
    }
}
