package dev.vexsoft.core.paper.profile;

import dev.vexsoft.core.api.world.WorldKey;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

/** Serializable native personal-profile state; platform objects never enter persistent containers. */
public record PaperProfileState(
    List<byte[]> inventory,
    List<byte[]> enderChest,
    byte[] cursor,
    int heldSlot,
    int level,
    int totalExperience,
    float experience,
    int fireTicks,
    int remainingAir,
    int foodLevel,
    float saturation,
    float exhaustion,
    List<Effect> effects,
    List<String> possessionIdentities,
    long capturedAt,
    Position position
) {

    private static final int LEGACY_INVENTORY_SIZE = 41;
    private static final int INVENTORY_SIZE = 43;
    private static final int ENDER_CHEST_SIZE = 27;

    /** Bounds one native snapshot including Paper 26.2's body/saddle slots, ender chest and cursor. */
    public static final int MAXIMUM_NATIVE_POSSESSIONS = INVENTORY_SIZE + ENDER_CHEST_SIZE + 1;

    /** Reads older snapshots with default hunger and no stored potion effects. */
    public PaperProfileState(
        final List<byte[]> inventory, final List<byte[]> enderChest, final byte[] cursor, final int heldSlot,
        final int level, final int totalExperience, final float experience, final int fireTicks, final int remainingAir
    ) {
        this(inventory, enderChest, cursor, heldSlot, level, totalExperience, experience, fireTicks, remainingAir,
            20, 5, 0, List.of());
    }

    /** Preserves the original native-state constructor without requiring a game identity index. */
    public PaperProfileState(
        final List<byte[]> inventory, final List<byte[]> enderChest, final byte[] cursor, final int heldSlot,
        final int level, final int totalExperience, final float experience, final int fireTicks, final int remainingAir,
        final int foodLevel, final float saturation, final float exhaustion, final List<Effect> effects
    ) {
        this(inventory, enderChest, cursor, heldSlot, level, totalExperience, experience, fireTicks, remainingAir,
            foodLevel, saturation, exhaustion, effects, List.of());
    }

    /** Preserves snapshots constructed without wall-clock timing or an optional game position. */
    public PaperProfileState(
        final List<byte[]> inventory, final List<byte[]> enderChest, final byte[] cursor, final int heldSlot,
        final int level, final int totalExperience, final float experience, final int fireTicks, final int remainingAir,
        final int foodLevel, final float saturation, final float exhaustion, final List<Effect> effects,
        final List<String> possessionIdentities
    ) {
        this(inventory, enderChest, cursor, heldSlot, level, totalExperience, experience, fireTicks, remainingAir,
            foodLevel, saturation, exhaustion, effects, possessionIdentities, 0, null);
    }

    /** Validates and defensively copies native state and its unique-possession index. */
    public PaperProfileState {
        inventory = inventory.stream().map(byte[]::clone).toList();
        enderChest = enderChest.stream().map(byte[]::clone).toList();
        cursor = cursor.clone();
        effects = effects == null ? List.of() : List.copyOf(effects);
        possessionIdentities = possessionIdentities == null ? List.of() : List.copyOf(possessionIdentities);
        // Older saves predate the body/saddle equipment slots; retain them for padded restoration.
        if (inventory.size() < LEGACY_INVENTORY_SIZE || inventory.size() > INVENTORY_SIZE) {
            throw new IllegalArgumentException("Invalid native profile inventory size: " + inventory.size());
        }
        if (enderChest.size() != ENDER_CHEST_SIZE) {
            throw new IllegalArgumentException("Invalid native profile ender chest size: " + enderChest.size());
        }
        if (possessionIdentities.size() > inventory.size() + enderChest.size() + 1) {
            throw new IllegalArgumentException("Too many unique native possessions");
        }
        if (possessionIdentities.stream().distinct().count() != possessionIdentities.size()) {
            throw new IllegalArgumentException("Duplicated unique native possession");
        }
        if (capturedAt < 0 || heldSlot < 0 || heldSlot > 8
            || level < 0 || totalExperience < 0 || !Float.isFinite(experience) || experience < 0 || experience > 1
            || fireTicks < 0 || remainingAir < 0 || foodLevel < 0 || foodLevel > 20
            || !Float.isFinite(saturation) || saturation < 0 || saturation > 20
            || !Float.isFinite(exhaustion) || exhaustion < 0 || exhaustion > 40 || effects.size() > 256) {
            throw new IllegalArgumentException("Invalid native profile state");
        }
    }

    @Override
    public List<byte[]> inventory() {
        return inventory.stream().map(byte[]::clone).toList();
    }

    @Override
    public List<byte[]> enderChest() {
        return enderChest.stream().map(byte[]::clone).toList();
    }

    @Override
    public byte[] cursor() {
        return cursor.clone();
    }

    /** Compares gameplay state independently of capture time and freshly allocated serialized item arrays. */
    public boolean sameState(final PaperProfileState other) {
        return other != null && heldSlot == other.heldSlot && level == other.level
            && totalExperience == other.totalExperience && experience == other.experience
            && fireTicks == other.fireTicks && remainingAir == other.remainingAir && foodLevel == other.foodLevel
            && saturation == other.saturation && exhaustion == other.exhaustion && effects.equals(other.effects)
            && possessionIdentities.equals(other.possessionIdentities) && Objects.equals(position, other.position)
            && Arrays.equals(cursor, other.cursor) && sameItems(inventory, other.inventory)
            && sameItems(enderChest, other.enderChest);
    }

    private static boolean sameItems(final List<byte[]> left, final List<byte[]> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (!Arrays.equals(left.get(index), right.get(index))) {
                return false;
            }
        }
        return true;
    }

    /** Computes remaining wall-clock effect duration without restarting effects on every activation. */
    public int remainingDuration(final Effect effect, final long now) {
        if (effect.duration() == -1 || capturedAt == 0) {
            return effect.duration();
        }
        return (int) Math.max(0, effect.duration() - Math.max(0, now - capturedAt) / 50L);
    }

    /** Stores an optional position using the Foundation's persistent world key. */
    public record Position(String world, double x, double y, double z, float yaw, float pitch) {

        /** Validates the namespaced world and finite coordinates before persistence. */
        public Position {
            WorldKey.parse(world);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("Invalid saved profile position");
            }
        }
    }

    /** Stores a namespaced native effect without retaining a Bukkit object. */
    public record Effect(String type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {

        /** Validates a stable effect key and native duration/amplifier. */
        public Effect {
            if (type == null || !type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || duration < -1 || amplifier < 0) {
                throw new IllegalArgumentException("Invalid native effect");
            }
        }
    }

    /** Creates an empty first-activation save without importing another profile's inventory. */
    public static PaperProfileState empty() {
        return new PaperProfileState(emptySlots(INVENTORY_SIZE), emptySlots(ENDER_CHEST_SIZE), new byte[0],
            0, 0, 0, 0, 0, 300);
    }

    private static List<byte[]> emptySlots(final int count) {
        return IntStream.range(0, count).mapToObj(ignored -> new byte[0]).toList();
    }
}
