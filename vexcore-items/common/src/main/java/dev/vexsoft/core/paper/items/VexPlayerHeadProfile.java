package dev.vexsoft.core.paper.items;

import java.util.Base64;
import java.util.Objects;

/**
 * Version-independent player-head texture component.
 *
 * <p>The texture value is the Base64-encoded Mojang {@code textures} profile property. A
 * signature is optional because custom server-owned textures are normally unsigned.</p>
 *
 * @param texture Base64-encoded textures property
 * @param signature optional Mojang property signature
 */
public record VexPlayerHeadProfile(String texture, String signature) {

  /** Creates an unsigned custom player-head profile. */
  public VexPlayerHeadProfile(final String texture) {
    this(texture, null);
  }

  /** Validates the stable profile representation before it reaches a version adapter. */
  public VexPlayerHeadProfile {
    texture = Objects.requireNonNull(texture, "texture").trim();
    if (texture.isEmpty()) {
      throw new IllegalArgumentException("texture must not be blank");
    }
    if (texture.length() > Short.MAX_VALUE) {
      throw new IllegalArgumentException("texture must not exceed 32767 characters");
    }
    try {
      Base64.getDecoder().decode(texture);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("texture must be valid Base64", exception);
    }
    if (signature != null && signature.length() > 1024) {
      throw new IllegalArgumentException("signature must not exceed 1024 characters");
    }
  }
}
