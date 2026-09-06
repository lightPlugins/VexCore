package dev.vexsoft.core.paper.screenui;

/** Bounded pack-backed scale presets. Scaling changes geometry, never text layout or packet cadence. */
public final class UiScale {
  private UiScale() { }

  public static double validate(double scale) {
    int tenth = (int) Math.round(scale * 10);
    if (!Double.isFinite(scale) || tenth < 5 || tenth > 10 || Math.abs(scale * 10 - tenth) > 0.000001) {
      throw new IllegalArgumentException("UI scale must be 0.5, 0.6, 0.7, 0.8, 0.9 or 1.0");
    }
    return tenth / 10.0;
  }

  public static double resolve(double scale, boolean compact) {
    return validate(scale == 0 ? (compact ? 0.5 : 1.0) : scale);
  }
}
