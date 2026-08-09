package dev.vexsoft.core.paper.items.internal;

import dev.vexsoft.core.paper.items.VexComponentData;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Contains version-independent component operations for one item build
 */
@Getter
public final class VexComponentPatch {

  private final Map<VexComponentData<?>, VexComponentOperation> operations;

  public VexComponentPatch(
      final Map<VexComponentData<?>, VexComponentOperation> operations
  ) {
    this.operations = Map.copyOf(new LinkedHashMap<>(operations));
  }
}
