package dev.vexsoft.core.paper.items;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public final class VexArmorComponentsTest {

    @Test
    public void rgbIncludesBlackAndWhiteButRejectsAlphaAndNegativeValues() {
        assertEquals(0, VexComponentData.DYED_COLOR.normalize(0));
        assertEquals(0xFFFFFF, VexComponentData.DYED_COLOR.normalize(0xFFFFFF));
        assertThrows(IllegalArgumentException.class, () -> VexComponentData.DYED_COLOR.normalize(-1));
        assertThrows(IllegalArgumentException.class, () -> VexComponentData.DYED_COLOR.normalize(0x1000000));
    }

    @Test
    public void hiddenComponentsAreImmutableAndCannotAccidentallyHideCustomLore() {
        var source = new HashSet<>(Set.of(VexComponentKey.TRIM, VexComponentKey.UNBREAKABLE));
        var display = new VexTooltipDisplay(false, source);

        source.clear();

        assertEquals(2, display.hiddenComponents().size());
        assertFalse(display.hideTooltip());
        assertThrows(IllegalArgumentException.class, () -> new VexTooltipDisplay(false, Set.of(VexComponentKey.LORE)));
        assertThrows(UnsupportedOperationException.class, () -> display.hiddenComponents().clear());
    }

    @Test
    public void emptyAttributesRemainAnExplicitPhysicalReplacement() {
        assertTrue(VexComponentData.ATTRIBUTE_MODIFIERS.normalize(VexItemAttributes.empty()).entries().isEmpty());
        assertEquals(VexComponentTarget.ITEM, VexComponentData.ATTRIBUTE_MODIFIERS.getTarget());
        assertEquals(VexComponentTarget.ITEM, VexComponentData.TRIM.getTarget());
        assertEquals(VexComponentTarget.ITEM, VexComponentData.TOOLTIP_DISPLAY.getTarget());
    }
}
