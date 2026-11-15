package dev.vexsoft.core.paper.items.v26_3;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.items.service.ItemComponentAdapterService;
import dev.vexsoft.core.paper.items.service.v26_3.VexItemComponentAdapterService;
import dev.vexsoft.core.paper.items.version.ItemVersionDefinition;
import java.util.Set;

@Dependencies
public class V26_3ItemVersionDefinition implements ItemVersionDefinition {

    public V26_3ItemVersionDefinition(final VexServiceRegistry services) {
    }

    @Override
    public String getAdapterVersion() {
        return "26.3";
    }

    @Override
    public Set<String> getSupportedVersions() {
        return Set.of("26.3");
    }

    @Override
    public Class<? extends ItemComponentAdapterService> getComponentAdapter() {
        return VexItemComponentAdapterService.class;
    }
}
