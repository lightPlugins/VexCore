package dev.vexsoft.core.paper.localization;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import lombok.experimental.UtilityClass;

@UtilityClass
public class LocalizationResourceScanner {

  /** Returns every bundled YAML file below the languages directory */
  public static List<String> scan(final Path pluginFile) {
    try (ZipFile zip = new ZipFile(pluginFile.toFile())) {
      return zip.stream()
          .filter(entry -> !entry.isDirectory())
          .map(entry -> entry.getName().replace('\\', '/'))
          .filter(name -> name.startsWith("languages/") && name.endsWith(".yml"))
          .sorted()
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to scan language resources in " + pluginFile, exception);
    }
  }
}
