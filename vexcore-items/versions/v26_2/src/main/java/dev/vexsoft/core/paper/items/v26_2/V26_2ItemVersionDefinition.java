package dev.vexsoft.core.paper.items.v26_2;

import dev.vexsoft.core.paper.service.items.v26_2.VexItemComponentAdapterService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.items.service.ItemComponentAdapterService;
import dev.vexsoft.core.paper.items.version.ItemVersionDefinition;
import java.util.Set;

@Dependencies
public class V26_2ItemVersionDefinition implements ItemVersionDefinition {

  public V26_2ItemVersionDefinition(final VexServiceRegistry services) {
  }

  @Override
  public String getAdapterVersion() {
    return "26.2";
  }

  @Override
  public Set<String> getSupportedVersions() {
    return Set.of("26.2");
  }

  @Override
  public Class<? extends ItemComponentAdapterService> getComponentAdapter() {
    return VexItemComponentAdapterService.class;
  }
}
