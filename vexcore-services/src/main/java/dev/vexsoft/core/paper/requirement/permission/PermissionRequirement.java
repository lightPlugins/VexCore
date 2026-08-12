package dev.vexsoft.core.paper.requirement.permission;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.requirement.CompiledRequirement;
import dev.vexsoft.core.requirement.Requirement;
import dev.vexsoft.core.requirement.RequirementResult;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Online-player {@code permission} requirement using AND semantics for lists. */
@Dependencies
public final class PermissionRequirement implements Requirement {

  /** Validates service construction. */
  public PermissionRequirement(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public CompiledRequirement compile(final Object value) {
    List<String> permissions;
    if (value instanceof List<?> list) {
      permissions = list.stream().map(Objects::toString).toList();
    } else {
      permissions = List.of(Objects.toString(value));
    }
    if (permissions.isEmpty() || permissions.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("permission requirement must contain permission nodes");
    }
    return new Compiled(permissions);
  }

  private record Compiled(List<String> permissions) implements CompiledRequirement {

    @Override
    public RequirementResult test(final PlayerExecutionContext context) {
      Player player = context.player().requirePlatformPlayer(Player.class);
      return permissions.stream().allMatch(player::hasPermission)
          ? RequirementResult.success()
          : RequirementResult.missing("Missing permission");
    }

    @Override
    public Component describe(final PlayerExecutionContext context) {
      Player player = context.player().requirePlatformPlayer(Player.class);
      Component result = Component.empty();
      for (int index = 0; index < permissions.size(); index++) {
        String permission = permissions.get(index);
        boolean satisfied = player.hasPermission(permission);
        if (index > 0) {
          result = result.append(Component.text(", ", NamedTextColor.DARK_GRAY));
        }
        result = result.append(Component.text(satisfied ? "✔ " : "✘ ",
                satisfied ? NamedTextColor.GREEN : NamedTextColor.RED))
            .append(Component.text(permission, NamedTextColor.GRAY));
      }
      return result;
    }

    @Override
    public List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
      Player player = context.player().requirePlatformPlayer(Player.class);
      return permissions.stream().map(permission -> {
        boolean satisfied = player.hasPermission(permission);
        Component fallback = Component.text(
                satisfied ? "\u2714 " : "\u2718 ",
                satisfied ? NamedTextColor.GREEN : NamedTextColor.RED
            )
            .append(Component.text(permission, NamedTextColor.GRAY));
        return new ExecutionDescription(
            satisfied ? "satisfied" : "missing",
            Map.of(
                "permission", Component.text(permission),
                "state_symbol", Component.text(
                    satisfied ? "\u2714" : "\u2718",
                    satisfied ? NamedTextColor.GREEN : NamedTextColor.RED
                )
            ),
            fallback
        );
      }).toList();
    }
  }
}
