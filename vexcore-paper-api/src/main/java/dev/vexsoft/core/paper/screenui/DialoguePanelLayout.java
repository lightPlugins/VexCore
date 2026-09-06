package dev.vexsoft.core.paper.screenui;

import java.util.Objects;

/** Fixed-size panel with inward pivot at its screen anchor. All dimensions are GUI pixels. */
public record DialoguePanelLayout(ScreenAnchor anchor, int offsetX, int offsetY,
    int paddingX, int paddingY, int nameGap, int lineSpacing) {
  public DialoguePanelLayout {
    Objects.requireNonNull(anchor, "anchor");
    if (Math.abs((long) offsetX) > 256 || Math.abs((long) offsetY) > 256
        || paddingX < 2 || paddingX > 100 || paddingY < 2 || paddingY > 16
        || nameGap < 0 || nameGap > 12 || lineSpacing < 0 || lineSpacing > 12
        || 96 - 2 * paddingY - 24 - nameGap - 6 < 36 + 2 * lineSpacing) {
      throw new IllegalArgumentException("Invalid dialogue panel dimensions");
    }
  }

  public static DialoguePanelLayout defaults() {
    return new DialoguePanelLayout(ScreenAnchor.BOTTOM_CENTER, 0, -48, 10, 8, 4, 2);
  }

  public int panelX() { return offsetX - anchor.ordinal() % 3 * 140; }
  public int panelY() { return offsetY - anchor.ordinal() / 3 * 48; }
  public int textX() { return panelX() + paddingX; }
  public int nameY() { return panelY() + paddingY; }
  public int bodyY() { return nameY() + 12 + nameGap; }
  public int textWidth() { return 280 - 2 * paddingX; }
  public int hintY() { return panelY() + 96 - paddingY - 12; }
  public int linesPerPage() { return 3; }
}
