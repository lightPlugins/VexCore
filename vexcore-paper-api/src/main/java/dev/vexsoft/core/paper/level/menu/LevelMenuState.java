package dev.vexsoft.core.paper.level.menu;

import dev.vexsoft.core.level.claim.LevelClaimPreview;

/** Visual state of one level entry in a level menu. */
public enum LevelMenuState {
  CLAIMED,
  CLAIMABLE,
  REQUIREMENTS_NOT_MET,
  COSTS_NOT_AFFORDABLE,
  LOCKED;

  /** Resolves a displayed level against the live preview of the next sequential claim. */
  public static LevelMenuState resolve(
      final int displayedLevel,
      final LevelClaimPreview nextClaim
  ) {
    if (displayedLevel <= nextClaim.state().claimedLevel()) {
      return CLAIMED;
    }
    if (displayedLevel != nextClaim.level()) {
      return LOCKED;
    }
    return switch (nextClaim.status()) {
      case READY -> CLAIMABLE;
      case REQUIREMENTS_NOT_MET -> REQUIREMENTS_NOT_MET;
      case COSTS_NOT_AFFORDABLE -> COSTS_NOT_AFFORDABLE;
      default -> LOCKED;
    };
  }
}
