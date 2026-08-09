package dev.vexsoft.core.paper.items.internal;

import dev.vexsoft.core.paper.items.VexComponentData;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class VexComponentPatchTest {

  @Test
  public void keepsAnImmutableOperationSnapshot() {
    Map<VexComponentData<?>, VexComponentOperation> source = new LinkedHashMap<>();
    source.put(VexComponentData.DAMAGE, VexComponentOperation.set(5));
    VexComponentPatch patch = new VexComponentPatch(source);

    source.clear();

    assertEquals(1, patch.getOperations().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> patch.getOperations().clear()
    );
  }
}
