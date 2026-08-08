package dev.vexsoft.core.paper.reactor.condition;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.condition.CompiledCondition;
import dev.vexsoft.core.gameplay.reactor.condition.Condition;
import dev.vexsoft.core.gameplay.reactor.context.PlayerReactorContext;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Player;

@ReactorId("permission")
@Dependencies
public final class PermissionCondition implements Condition<PlayerReactorContext> {

  public PermissionCondition(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public Class<PlayerReactorContext> getContextType() {
    return PlayerReactorContext.class;
  }

  @Override
  public CompiledCondition<PlayerReactorContext> compile(final Map<String, Object> arguments) {
    Object configured = Objects.requireNonNull(arguments, "arguments").get("permission");
    if (!(configured instanceof String permission) || permission.isBlank()) {
      throw new IllegalArgumentException("Permission condition requires a permission string");
    }
    String checkedPermission = permission.trim();
    return context -> context.getPlayer()
        .requirePlatformPlayer(Player.class)
        .hasPermission(checkedPermission);
  }
}
