package dev.vexsoft.core.paper.packets.v26_2.display;

import dev.vexsoft.core.paper.packets.display.FakeItemDisplayUpdate;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayUpdate;
import io.papermc.paper.adventure.PaperAdventure;
import lombok.experimental.UtilityClass;
import net.minecraft.world.entity.Display;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

@UtilityClass
public class V26_2DisplayUpdates {

  public static byte textFlags(final FakeTextDisplayRequest request) {
    byte flags = 0;
    flags = setFlag(flags, Display.TextDisplay.FLAG_SHADOW, request.isShadowed());
    flags = setFlag(flags, Display.TextDisplay.FLAG_SEE_THROUGH, request.isSeeThrough());
    flags = setFlag(
        flags,
        Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND,
        request.isDefaultBackground()
    );
    return V26_2DisplayMapper.applyAlignment(flags, request.getAlignment());
  }

  public static void applyText(
      final Display.TextDisplay display,
      final FakeTextDisplayUpdate update
  ) {
    if (update.getText() != null) {
      display.setText(PaperAdventure.asVanilla(update.getText()));
    }
    if (update.getLineWidth() != null) {
      display.getEntityData().set(Display.TextDisplay.DATA_LINE_WIDTH_ID, update.getLineWidth(), true);
    }
    if (update.getBackgroundColor() != null) {
      display.getEntityData().set(
          Display.TextDisplay.DATA_BACKGROUND_COLOR_ID,
          update.getBackgroundColor(),
          true
      );
    }
    if (update.getTextOpacity() != null) {
      display.setTextOpacity(update.getTextOpacity());
    }
    byte flags = display.getFlags();
    if (update.getShadowed() != null) {
      flags = setFlag(flags, Display.TextDisplay.FLAG_SHADOW, update.getShadowed());
    }
    if (update.getSeeThrough() != null) {
      flags = setFlag(flags, Display.TextDisplay.FLAG_SEE_THROUGH, update.getSeeThrough());
    }
    if (update.getDefaultBackground() != null) {
      flags = setFlag(
          flags,
          Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND,
          update.getDefaultBackground()
      );
    }
    if (update.getAlignment() != null) {
      flags = V26_2DisplayMapper.applyAlignment(flags, update.getAlignment());
    }
    display.setFlags(flags);
    applyBase(display, update);
  }

  public static void applyItem(
      final Display.ItemDisplay display,
      final FakeItemDisplayUpdate update
  ) {
    if (update.getItemStack() != null) {
      display.setItemStack(CraftItemStack.asNMSCopy(update.getItemStack()));
    }
    if (update.getItemTransform() != null) {
      display.setItemTransform(V26_2DisplayMapper.toNms(update.getItemTransform()));
    }
    if (update.getGlowing() != null) {
      display.setGlowingTag(update.getGlowing());
    }
    applyBase(display, update);
  }

  private static void applyBase(final Display display, final FakeTextDisplayUpdate update) {
    if (update.getTransformation() != null) {
      display.setTransformation(V26_2DisplayMapper.toNms(update.getTransformation()));
    }
    if (update.getBillboard() != null) {
      display.setBillboardConstraints(V26_2DisplayMapper.toNms(update.getBillboard()));
    }
    if (update.getBrightness() != null) {
      display.setBrightnessOverride(V26_2DisplayMapper.toNms(update.getBrightness()));
    }
    applyDimensions(
        display, update.getViewRange(), update.getShadowRadius(), update.getShadowStrength(),
        update.getDisplayWidth(), update.getDisplayHeight(), update.getInterpolationDelay(),
        update.getInterpolationDuration(), update.getTeleportDuration()
    );
  }

  private static void applyBase(final Display display, final FakeItemDisplayUpdate update) {
    if (update.getTransformation() != null) {
      display.setTransformation(V26_2DisplayMapper.toNms(update.getTransformation()));
    }
    if (update.getBillboard() != null) {
      display.setBillboardConstraints(V26_2DisplayMapper.toNms(update.getBillboard()));
    }
    if (update.getBrightness() != null) {
      display.setBrightnessOverride(V26_2DisplayMapper.toNms(update.getBrightness()));
    }
    applyDimensions(
        display, update.getViewRange(), update.getShadowRadius(), update.getShadowStrength(),
        update.getDisplayWidth(), update.getDisplayHeight(), update.getInterpolationDelay(),
        update.getInterpolationDuration(), update.getTeleportDuration()
    );
  }

  private static void applyDimensions(
      final Display display,
      final Float viewRange,
      final Float shadowRadius,
      final Float shadowStrength,
      final Float width,
      final Float height,
      final Integer interpolationDelay,
      final Integer interpolationDuration,
      final Integer teleportDuration
  ) {
    if (viewRange != null) {
      display.setViewRange(viewRange);
    }
    if (shadowRadius != null) {
      display.setShadowRadius(shadowRadius);
    }
    if (shadowStrength != null) {
      display.setShadowStrength(shadowStrength);
    }
    if (width != null) {
      display.setWidth(width);
    }
    if (height != null) {
      display.setHeight(height);
    }
    if (interpolationDelay != null) {
      display.setTransformationInterpolationDelay(interpolationDelay);
    }
    if (interpolationDuration != null) {
      display.setTransformationInterpolationDuration(interpolationDuration);
    }
    if (teleportDuration != null) {
      display.getEntityData().set(
          Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID,
          teleportDuration,
          true
      );
    }
  }

  private static byte setFlag(final byte flags, final byte flag, final boolean enabled) {
    return enabled ? (byte) (flags | flag) : (byte) (flags & ~flag);
  }
}
