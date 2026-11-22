package dev.vexsoft.core.paper.vexui.template.profession;

import dev.vexsoft.core.paper.vexui.template.VexUIIcon;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Viewer-specific progress and level rewards for the available professions. */
public record VexUIProfessionSnapshot(boolean valid, List<Profession> professions, boolean pending) {

    /** Copies the profession list and validates its identifiers. */
    public VexUIProfessionSnapshot {
        professions = List.copyOf(professions);
        if (professions.stream().map(Profession::id).distinct().count() != professions.size()) {
            throw new IllegalArgumentException("Profession identifiers must be unique");
        }
    }

    /** One profession including its current personal-profile progress and presentation metadata. */
    public record Profession(
        String id,
        Component name,
        List<Component> description,
        VexUIIcon icon,
        int level,
        int maximum,
        double experience,
        double required,
        double progress,
        List<Milestone> milestones
    ) {
        /** Copies presentation data and validates finite progression values. */
        public Profession {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            description = List.copyOf(description);
            milestones = List.copyOf(milestones);
            if (id.isBlank() || level < 0 || maximum < 1 || maximum > 100_000 || level > maximum
                || !Double.isFinite(experience) || !Double.isFinite(required) || !Double.isFinite(progress)
                || experience < 0 || required < 0) {
                throw new IllegalArgumentException("Invalid profession progress");
            }
            progress = level == maximum ? 1 : Math.clamp(progress, 0, 1);
        }

        /** Counts available bundles without depending on the selected UI filter. */
        public int available() {
            return (int) milestones.stream().filter(value -> value.status() == Status.AVAILABLE).count();
        }
    }

    /** One localized reward shown for a milestone. */
    public record Entry(String id, Component name, List<Component> details, VexUIIcon icon) {
        /** Copies complete styled detail lines. */
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            details = List.copyOf(details);
            if (id.isBlank()) {
                throw new IllegalArgumentException("Invalid profession entry");
            }
        }
    }

    /** An atomic milestone bundle; its list may span several presentation pages. */
    public record Milestone(int level, Status status, List<Entry> rewards) {
        /** Copies the preview entries and validates a positive milestone level. */
        public Milestone {
            Objects.requireNonNull(status, "status");
            rewards = List.copyOf(rewards);
            if (level < 1) {
                throw new IllegalArgumentException("Milestone levels must be positive");
            }
        }
    }

    /** A level's relationship to manual reward collection. */
    public enum Status {
        LOCKED,
        REACHED,
        AVAILABLE,
        CLAIMED
    }
}
