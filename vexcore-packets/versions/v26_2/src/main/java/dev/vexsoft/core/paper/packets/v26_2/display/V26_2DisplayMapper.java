package dev.vexsoft.core.paper.packets.v26_2.display;

import com.mojang.math.Transformation;
import dev.vexsoft.core.paper.packets.display.DisplayBillboard;
import dev.vexsoft.core.paper.packets.display.DisplayBrightness;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import dev.vexsoft.core.paper.packets.display.DisplayTransformation;
import dev.vexsoft.core.paper.packets.display.ItemDisplayTransform;
import dev.vexsoft.core.paper.packets.display.TextDisplayAlignment;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.scores.TeamColor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@UtilityClass
public class V26_2DisplayMapper {

  public static Transformation toNms(final DisplayTransformation transformation) {
    return new Transformation(
        new Vector3f(
            transformation.getTranslationX(),
            transformation.getTranslationY(),
            transformation.getTranslationZ()
        ),
        new Quaternionf(
            transformation.getLeftRotationX(),
            transformation.getLeftRotationY(),
            transformation.getLeftRotationZ(),
            transformation.getLeftRotationW()
        ),
        new Vector3f(
            transformation.getScaleX(),
            transformation.getScaleY(),
            transformation.getScaleZ()
        ),
        new Quaternionf(
            transformation.getRightRotationX(),
            transformation.getRightRotationY(),
            transformation.getRightRotationZ(),
            transformation.getRightRotationW()
        )
    );
  }

  public static Display.BillboardConstraints toNms(final DisplayBillboard billboard) {
    return switch (billboard) {
      case FIXED -> Display.BillboardConstraints.FIXED;
      case VERTICAL -> Display.BillboardConstraints.VERTICAL;
      case HORIZONTAL -> Display.BillboardConstraints.HORIZONTAL;
      case CENTER -> Display.BillboardConstraints.CENTER;
    };
  }

  public static Brightness toNms(final DisplayBrightness brightness) {
    return new Brightness(brightness.getBlock(), brightness.getSky());
  }

  public static ItemDisplayContext toNms(final ItemDisplayTransform transform) {
    return switch (transform) {
      case NONE -> ItemDisplayContext.NONE;
      case THIRD_PERSON_LEFT_HAND -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
      case THIRD_PERSON_RIGHT_HAND -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
      case FIRST_PERSON_LEFT_HAND -> ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
      case FIRST_PERSON_RIGHT_HAND -> ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
      case HEAD -> ItemDisplayContext.HEAD;
      case GUI -> ItemDisplayContext.GUI;
      case GROUND -> ItemDisplayContext.GROUND;
      case FIXED -> ItemDisplayContext.FIXED;
    };
  }

  public static TeamColor toNms(final DisplayGlowColor glowColor) {
    if (glowColor == null) {
      return TeamColor.WHITE;
    }
    TeamColor nearest = TeamColor.WHITE;
    int nearestDistance = Integer.MAX_VALUE;
    for (TeamColor color : TeamColor.VALUES) {
      int distance = colorDistance(glowColor.getRgb(), color.rgb());
      if (distance < nearestDistance) {
        nearest = color;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  public static byte applyAlignment(final byte current, final TextDisplayAlignment alignment) {
    byte flags = (byte) (current
        & ~Display.TextDisplay.FLAG_ALIGN_LEFT
        & ~Display.TextDisplay.FLAG_ALIGN_RIGHT);
    return switch (alignment) {
      case CENTER -> flags;
      case LEFT -> (byte) (flags | Display.TextDisplay.FLAG_ALIGN_LEFT);
      case RIGHT -> (byte) (flags | Display.TextDisplay.FLAG_ALIGN_RIGHT);
    };
  }

  private static int colorDistance(final int first, final int second) {
    int red = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
    int green = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
    int blue = (first & 0xFF) - (second & 0xFF);
    return red * red + green * green + blue * blue;
  }
}
