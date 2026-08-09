package dev.vexsoft.core.common.service.localization;


import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.DataContainerRegistry;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;

@Dependencies
public final class VexCorePlayerData implements PlayerDataDefinition {

  static final DataContainerKey<LanguageData> LANGUAGE = DataContainerKey.of(
      "language",
      LanguageData.class,
      LanguageData::new
  );

  public VexCorePlayerData(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public void register(final DataContainerRegistry registry) {
    registry.register(LANGUAGE);
  }
}
