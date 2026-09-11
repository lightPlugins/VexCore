package dev.vexsoft.core.common.service.localization.editor;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import java.nio.file.Path;
import java.util.Collection;

/** Inspects and safely edits registered plugin localizations. */
public interface LocalizationEditorService extends VexService {

    /** Returns the registered owners whose localization resources can be edited. */
    Collection<LocalizationOwner> getOwners();

    /** Returns the languages available for the selected owner. */
    Collection<LanguageKey> getLanguages(String ownerName);

    /** Lists localization files and subdirectories inside the selected language directory. */
    Collection<LocalizationBrowserNode> browse(String ownerName, LanguageKey language, Path relativeDirectory);

    /** Returns the file's localized entries, including entries supplied by the English fallback. */
    Collection<LocalizationEntryView> getEntries(String ownerName, LanguageKey language, Path relativeFile);

    /** Saves one localized value and reloads its owner, restoring the previous file if reload fails. */
    void update(String ownerName, LanguageKey language, Path relativeFile, String key, LocalizationValue value);
}
