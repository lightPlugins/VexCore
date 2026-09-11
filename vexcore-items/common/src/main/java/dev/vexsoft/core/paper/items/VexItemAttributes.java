package dev.vexsoft.core.paper.items;

import java.util.List;
import java.util.Objects;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

/** Complete replacement of an item's attribute modifiers, including prototype defaults. */
public record VexItemAttributes(List<Entry> entries) {

    /** Copies the complete modifier list for immutable item configuration. */
    public VexItemAttributes {
        entries = List.copyOf(entries);
    }

    /** Returns an explicit empty modifier list, overriding vanilla item defaults. */
    public static VexItemAttributes empty() {
        return new VexItemAttributes(List.of());
    }

    /** Associates one attribute with its modifier. */
    public record Entry(Attribute attribute, AttributeModifier modifier) {

        /** Associates one attribute with its modifier. */
        public Entry {
            Objects.requireNonNull(attribute, "attribute");
            Objects.requireNonNull(modifier, "modifier");
        }
    }
}
