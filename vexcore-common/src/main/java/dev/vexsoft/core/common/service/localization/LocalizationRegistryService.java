package dev.vexsoft.core.common.service.localization;


import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Collection;
import java.util.Map;

/**
 * Stores and reloads the localization caches registered by plugins
 */
public interface LocalizationRegistryService extends VexService {

  /** Loads and registers every localization supplied by an owner */
  void register(LocalizationOwner owner);

  /** Removes the localization cache registered by an owner */
  void unregister(LocalizationOwner owner);

  /** Resolves a localized message from an owner's cache */
  LocalizedMessage resolve(
      LocalizationOwner owner,
      LanguageKey language,
      String key,
      Map<String, String> replacements
  );

  /** Resolves a localized message from a named owner's cache */
  LocalizedMessage resolve(
      String ownerName,
      LanguageKey language,
      String key,
      Map<String, String> replacements
  );

  /** Returns every language loaded for the named owner */
  Collection<LanguageKey> getLanguages(String ownerName);

  /** Returns every currently registered localization owner. */
  Collection<LocalizationOwner> getOwners();

  /** Reloads the cache registered for a named owner. */
  void reload(String ownerName);

  /** Reloads the cache registered by an owner */
  void reload(LocalizationOwner owner);

  /** Reloads every registered localization cache */
  void reloadAll();
}
