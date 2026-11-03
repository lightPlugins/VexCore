package dev.vexsoft.core.paper.vexui;

/** Tintable pixel-frame skin; custom palettes reuse white geometry without rebuilding PNGs. */
public record VexUITheme(int background, int outerBorder, int lightBorder, int darkBorder) {

    public static final VexUITheme PANEL = new VexUITheme(0x292534, 0x17141F, 0x5A516C, 0x40364F);
    public static final VexUITheme BUTTON = new VexUITheme(0x201D29, 0x17141F, 0x5A516C, 0x40364F);
    public static final VexUITheme PRIMARY = new VexUITheme(0x40364F, 0x17141F, 0x756182, 0x5A516C);
    public static final VexUITheme SELECTED = new VexUITheme(0x38303F, 0x17141F, 0xB69ACB, 0x80708F);
    public static final VexUITheme DISABLED = new VexUITheme(0x292534, 0x17141F, 0x40364F, 0x332D43);
    public static final VexUITheme DANGER = new VexUITheme(0x432E3B, 0x211820, 0xA87786, 0x694653);

    /** Requires opaque 24-bit RGB tints for the four geometric frame roles. */
    public VexUITheme {
        if ((background | outerBorder | lightBorder | darkBorder) >>> 24 != 0) {
            throw new IllegalArgumentException("VexUI theme colors must be 24-bit RGB values");
        }
    }
}
