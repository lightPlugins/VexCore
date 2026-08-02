package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.DataContainerRegistry;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Objects;

@Dependencies
public final class VexCorePlayerData implements PlayerDataDefinition {

  public static final DataContainerKey<LanguageContainer> LANGUAGE = DataContainerKey.of(
      "language",
      LanguageContainer.class,
      LanguageContainer::new
  );

  public VexCorePlayerData(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public void register(final DataContainerRegistry registry) {
    registry.register(LANGUAGE);
  }
}
