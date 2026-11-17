package dev.vexsoft.core.paper.screenui.reward;

import net.kyori.adventure.text.format.TextColor;

/** Protocol-10 card geometry and frame-clock payload shared by every primitive in one card. */
public record RewardCardLayout(int slot, int previousSlot, long started, long moved, boolean pulse) {

    public static final int WIDTH = 224;
    public static final int HEIGHT = 40;
    public static final int GAP = 4;
    public static final double SCALE = 0.7;
    public static final double QUANTITY_SCALE = 1.5;
    public static final double QUANTITY_PULSE_SCALE = 1.5;
    public static final int CAPACITY = 4;
    public static final int LIFETIME = 73;
    public static final int REFLOW = 6;
    public static final int CLOCK = 120;
    public static final int CARRIER_BASE = 65536;
    public static final int CARRIER_STRIDE = 512;
    public static final int TEXT_Y_STRIDE = 32;

    /** Requires current and previous slots within the visible reward stack. */
    public RewardCardLayout {
        if (slot < 0 || slot >= CAPACITY || previousSlot < 0 || previousSlot >= CAPACITY) {
            throw new IllegalArgumentException("Invalid reward card slot");
        }
    }

    /** Encodes 3-bit tint channels, two wrapped clocks and a quantity-pulse flag without losing precision. */
    public TextColor payload(TextColor tint) {
        int rgb = ((tint.red() * 7 + 127) / 255 << 6)
            | ((tint.green() * 7 + 127) / 255 << 3) | (tint.blue() * 7 + 127) / 255;
        return TextColor.color(rgb | (int) Math.floorMod(started, CLOCK) << 9
            | (int) Math.floorMod(moved, CLOCK) << 16 | (pulse ? 1 << 23 : 0));
    }

    /** Identifies the generated font variant for this stack movement. */
    public String variant() {
        return slot + "_" + previousSlot;
    }

    /** Encodes one primitive: text 0, frame 1, icon 2, quantity 3. */
    public int ascent(int primitive) {
        if (primitive < 0 || primitive > 3) {
            throw new IllegalArgumentException("Invalid reward card primitive");
        }
        return carrierAscent((slot * CAPACITY + previousSlot) * 4 + primitive);
    }

    /** Returns the shared text or amount ascent. Slot transitions are carried horizontally. */
    public static int textAscent(boolean quantity) {
        return carrierAscent(quantity ? 3 : 0);
    }

    /** Encodes the local text Y coordinate and the current and previous card slots. */
    public int textTransportY(int y) {
        if (y < 0 || y >= TEXT_Y_STRIDE) {
            throw new IllegalArgumentException("Invalid reward text Y coordinate");
        }
        return (slot * CAPACITY + previousSlot) * TEXT_Y_STRIDE + y;
    }

    private static int carrierAscent(int carrier) {
        return 8 - (CARRIER_BASE + carrier * CARRIER_STRIDE + 128);
    }
}
