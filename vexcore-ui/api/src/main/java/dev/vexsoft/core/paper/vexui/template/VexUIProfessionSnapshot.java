package dev.vexsoft.core.paper.vexui.template;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Immutable UI projection; progression, availability and receipt contents remain domain-service decisions. */
public record VexUIProfessionSnapshot(boolean valid, List<Profession> professions, boolean pending) {

    /** Copies the complete, unfiltered profession list used for global reward aggregation. */
    public VexUIProfessionSnapshot {
        professions = List.copyOf(professions);
        if (professions.stream().map(Profession::id).distinct().count() != professions.size()) {
            throw new IllegalArgumentException("Profession identifiers must be unique");
        }
    }

    /** Returns the count of available milestone bundles across every profession and page. */
    public int available() {
        return professions.stream().mapToInt(Profession::available).sum();
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
        List<Milestone> milestones,
        List<Entry> sources,
        List<Entry> unlocks
    ) {
        /** Copies presentation data and validates finite progression values. */
        public Profession {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            description = List.copyOf(description);
            milestones = List.copyOf(milestones);
            sources = List.copyOf(sources);
            unlocks = List.copyOf(unlocks);
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

        /** Returns whether an available unlock has not yet been seen in the relevant list. */
        public boolean fresh() {
            return unlocks.stream().anyMatch(value -> value.available() && value.fresh());
        }
    }

    /** One localized item, reward description, source or unlock; detailed lines are independently paginated. */
    public record Entry(
        String id,
        Component name,
        List<Component> details,
        VexUIIcon icon,
        boolean available,
        boolean fresh,
        int requiredLevel
    ) {
        /** Copies complete styled detail lines. */
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            details = List.copyOf(details);
            if (id.isBlank() || requiredLevel < 0) {
                throw new IllegalArgumentException("Invalid profession entry");
            }
        }

        /** Creates an ordinary available presentation entry without an unread marker. */
        public Entry(final String id, final Component name, final List<Component> details, final VexUIIcon icon) {
            this(id, name, details, icon, true, false, 0);
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

    /** Authoritative claim result; failures never include a fabricated receipt. */
    public record Claim(boolean successful, List<Receipt> receipts) {
        /** Copies successful receipt data. */
        public Claim {
            receipts = List.copyOf(receipts);
            if (!successful && !receipts.isEmpty()) {
                throw new IllegalArgumentException("Failed claims cannot carry grants");
            }
        }
    }

    /** Actual grants with frozen profession/level provenance, independent of subsequent catalog reloads. */
    public record Receipt(String profession, Component name, int level, List<Entry> rewards) {
        /** Copies the exact granted entries. */
        public Receipt {
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(name, "name");
            rewards = List.copyOf(rewards);
        }
    }
}
