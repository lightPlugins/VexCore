package dev.vexsoft.core.paper.vexui;

/** Immutable node in a VexUI layout tree. */
public sealed interface VexUIElement permits VexUIRow, VexUIColumn, VexUIGrid, VexUIPanel,
    VexUIButton, VexUIText, VexUIItemIcon, VexUISpacer, VexUITexture, VexUIGridCell,
    VexUIProgressBar, VexUIParagraph {

    /** Returns this node's preferred dimensions. */
    VexUISize size();
}
