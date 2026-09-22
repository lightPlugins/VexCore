package dev.vexsoft.core.common.requirement.level;

import dev.vexsoft.core.api.service.level.LevelInstanceService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.level.LevelExecutionText;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.level.LevelRequirementDefinition;
import dev.vexsoft.core.requirement.CompiledRequirement;
import dev.vexsoft.core.requirement.Requirement;
import dev.vexsoft.core.requirement.RequirementResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Tests the reached level of an instance without consuming XP or checking reward claims. */
@Dependencies(LevelInstanceService.class)
public final class LevelRequirement implements Requirement {

    private final LevelInstanceService levels;

    public LevelRequirement(final VexServiceRegistry services) {
        levels = services.require(LevelInstanceService.class);
    }

    @Override
    public CompiledRequirement compile(final Object value) {
        Map<?, ?> configuration = LevelExecutionText.values(value);
        String id = Objects.toString(configuration.get("system"), "");
        int required = Integer.parseInt(Objects.toString(configuration.get("level"), ""));
        levels.validateReference(id, required);
        return new Compiled(id, required, levels);
    }

    private record Compiled(String id, int required, LevelInstanceService levels) implements CompiledRequirement {

        @Override
        public RequirementResult test(final PlayerExecutionContext context) {
            if (!levels.getInstances().containsKey(id)) {
                return RequirementResult.missing("Unknown level instance: " + id);
            }
            return levels.meets(context.player(), new LevelRequirementDefinition(id, required))
                ? RequirementResult.success()
                : RequirementResult.missing("Required level " + id + '/' + required);
        }

        @Override
        public Component describe(final PlayerExecutionContext context) {
            return levels.describeRequirement(context.player(), new LevelRequirementDefinition(id, required));
        }

        @Override
        public List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
            String state = test(context).satisfied() ? "satisfied" : "missing";
            return List.of(new ExecutionDescription(
                state, Map.of(
                "current", Component.text(current(context)), "required", Component.text(required)), describe(context)
            ));
        }

        private int current(final PlayerExecutionContext context) {
            return levels.getInstances().containsKey(id) ? levels.snapshot(context.player(), id).progress().level() : 0;
        }
    }
}
