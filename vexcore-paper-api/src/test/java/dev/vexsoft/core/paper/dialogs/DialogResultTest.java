package dev.vexsoft.core.paper.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public final class DialogResultTest {

    @Test
    public void exposesSubmittedValues() {
        DialogResult<String> result = DialogResult.value(DialogResultType.CONFIRMED, "Vex");

        assertTrue(result.isConfirmed());
        assertEquals("Vex", result.getValue().orElseThrow());
    }

    @Test
    public void keepsEmptyOutcomesValueFree() {
        DialogResult<String> result = DialogResult.empty(DialogResultType.TIMED_OUT);

        assertFalse(result.isConfirmed());
        assertTrue(result.getValue().isEmpty());
    }
}
