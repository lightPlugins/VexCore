package dev.vexsoft.core.paper.vexui.template;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Immutable travel presentation; routing, discoveries and journey cancellation remain caller-owned. */
public record VexUITravelSnapshot(
    boolean valid, List<Category> categories, Component journey, boolean travelling, boolean cancellable
) {
    /** Copies ordered categories and validates stable, unambiguous category identities. */
    public VexUITravelSnapshot {
        categories = List.copyOf(categories);
        Objects.requireNonNull(journey, "journey");
        if (categories.stream().map(Category::id).distinct().count() != categories.size()) {
            throw new IllegalArgumentException("Duplicate travel category");
        }
        if (cancellable && !travelling) {
            throw new IllegalArgumentException("An idle journey cannot be cancelled");
        }
    }

    /** One ordered category with complete hover text and a viewer-safe destination list. */
    public record Category(String id, Component name, Component tooltip, List<Destination> destinations) {
        /** Copies destinations and validates identities used by the independent destination pager. */
        public Category {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(tooltip, "tooltip");
            destinations = List.copyOf(destinations);
            if (id.isBlank() || destinations.stream().map(Destination::id).distinct().count() != destinations.size()) {
                throw new IllegalArgumentException("Invalid travel category");
            }
        }

        /** Counts discoveries independently of the currently displayed destination page. */
        public int discovered() {
            return (int) destinations.stream().filter(Destination::discovered).count();
        }
    }

    /** Safe display data; undiscovered destinations must already have anonymous names, icons and tooltips. */
    public record Destination(
        String id, Component name, Component status, Component tooltip, VexUIIcon icon,
        boolean discovered, boolean current, boolean enabled
    ) {
        /** Validates the presentation and prevents a hidden or current destination from becoming actionable. */
        public Destination {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(tooltip, "tooltip");
            Objects.requireNonNull(icon, "icon");
            if (id.isBlank() || enabled && (!discovered || current)) {
                throw new IllegalArgumentException("Invalid travel destination");
            }
        }
    }
}
