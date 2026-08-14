package dev.vexsoft.core.common.service.localization.editor;

import java.nio.file.Path;
import java.util.Objects;

/** A direct directory or YAML-file child in the localization browser. */
public record LocalizationBrowserNode(
    String name,
    Path relativePath,
    boolean directory,
    boolean inherited
) {

  public LocalizationBrowserNode {
    name = Objects.requireNonNull(name, "name");
    relativePath = Objects.requireNonNull(relativePath, "relativePath");
  }
}
