package dev.vexsoft.core.common.service.localization.editor;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import java.nio.file.Path;
import java.util.Collection;

/** Inspects and safely edits registered plugin localizations. */
public interface LocalizationEditorService extends VexService {

  Collection<LocalizationOwner> getOwners();

  Collection<LanguageKey> getLanguages(String ownerName);

  Collection<LocalizationBrowserNode> browse(
      String ownerName,
      LanguageKey language,
      Path relativeDirectory
  );

  Collection<LocalizationEntryView> getEntries(
      String ownerName,
      LanguageKey language,
      Path relativeFile
  );

  void update(
      String ownerName,
      LanguageKey language,
      Path relativeFile,
      String key,
      LocalizationValue value
  );
}
