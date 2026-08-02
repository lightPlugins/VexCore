package dev.vexsoft.core.api.localization;

import dev.vexsoft.core.api.service.ServiceOwner;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public interface LocalizationOwner extends ServiceOwner {

  /** Returns the directory containing this owner's external language files */
  public Path getLocalizationDirectory();

  /** Returns every bundled YAML language resource supplied by this owner */
  public Collection<String> getLocalizationResources();

  /** Opens a bundled language resource when it exists */
  public Optional<InputStream> getLocalizationResource(String resourcePath);

  /** Returns the localization key used as this owner's message prefix */
  public String getMessagePrefixKey();

  /** Reports a non-fatal warning produced while processing localization data */
  public void reportLocalizationWarning(String message, Throwable cause);
}
