package dev.vexsoft.core.item.v26_2;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.item.internal.ItemComponentAdapterService;
import dev.vexsoft.core.item.version.ItemVersionDefinition;
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
