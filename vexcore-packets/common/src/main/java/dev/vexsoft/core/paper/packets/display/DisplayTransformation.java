package dev.vexsoft.core.paper.packets.display;

import lombok.Builder;
import lombok.Value;

/** Immutable translation, quaternion rotation and scale applied to a display entity. */
@Value
@Builder(toBuilder = true)
public class DisplayTransformation {
  float translationX;
  float translationY;
  float translationZ;
  float leftRotationX;
  float leftRotationY;
  float leftRotationZ;
  float leftRotationW;
  float scaleX;
  float scaleY;
  float scaleZ;
  float rightRotationX;
  float rightRotationY;
  float rightRotationZ;
  float rightRotationW;

  /** Returns a transformation with zero translation, identity rotations and unit scale. */
  public static DisplayTransformation identity() {
    return builder()
        .leftRotationW(1.0F)
        .scaleX(1.0F)
        .scaleY(1.0F)
        .scaleZ(1.0F)
        .rightRotationW(1.0F)
        .build();
  }

  /** Returns an identity transformation with uniform scale on every axis. */
  public static DisplayTransformation scale(final float scale) {
    return identity().toBuilder().scaleX(scale).scaleY(scale).scaleZ(scale).build();
  }
}
