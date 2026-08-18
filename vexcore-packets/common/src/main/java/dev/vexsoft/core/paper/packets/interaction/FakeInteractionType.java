package dev.vexsoft.core.paper.packets.interaction;

/** Distinguishes attack and use input received by a virtual interaction entity. */
public enum FakeInteractionType {
  /** Attack input sent by the viewer's main hand. */
  LEFT_CLICK,
  /** Use input sent by either of the viewer's hands. */
  RIGHT_CLICK
}
