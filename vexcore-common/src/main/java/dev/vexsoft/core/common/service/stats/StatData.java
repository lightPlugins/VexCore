package dev.vexsoft.core.common.service.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

final class StatData {

    @Getter
    private Map<String, Double> permanentValues = new LinkedHashMap<>();

    public void setPermanentValues(final Map<String, Double> permanentValues) {
        this.permanentValues = new LinkedHashMap<>(permanentValues);
    }
}
