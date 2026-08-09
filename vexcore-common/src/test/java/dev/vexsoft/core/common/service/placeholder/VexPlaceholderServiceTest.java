package dev.vexsoft.core.common.service.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.placeholder.PlaceholderArguments;
import dev.vexsoft.core.placeholder.PlaceholderContext;
import dev.vexsoft.core.placeholder.PlaceholderId;
import dev.vexsoft.core.placeholder.VexPlaceholder;
import java.util.UUID;
import lombok.Value;
import org.junit.jupiter.api.Test;

class VexPlaceholderServiceTest {

  @Test
  void resolvesNamespacedDynamicAndLocalPlaceholders() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    VexServiceRegistry core = registry.scoped(new TestOwner("VexCore"));
    core.register(
        PlaceholderRegistryCoordinatorService.class,
        VexPlaceholderRegistryCoordinatorService.class
    );
    core.registerQueuedServices();
    VexServiceRegistry skills = registry.scoped(new TestOwner("VexSkills"));
    skills.register(PlaceholderService.class, VexPlaceholderService.class);
    skills.registerQueuedServices();
    skills.require(PlaceholderService.class).register(SkillPlaceholder.class);
    VexPlayer player = new VexPlayer(UUID.randomUUID(), "TestPlayer");

    String resolved = skills.require(PlaceholderService.class).resolve(
        PlaceholderContext.of(player).with("level", 42),
        "%vexskills_skill_mining_level% / %level% / %unknown_value%"
    );

    assertEquals("mining:level / 42 / %unknown_value%", resolved);
  }

  @Test
  void rejectsDuplicateOwnerPlaceholderIds() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    VexServiceRegistry core = registry.scoped(new TestOwner("VexCore"));
    core.register(
        PlaceholderRegistryCoordinatorService.class,
        VexPlaceholderRegistryCoordinatorService.class
    );
    core.registerQueuedServices();
    VexServiceRegistry skills = registry.scoped(new TestOwner("VexSkills"));
    skills.register(PlaceholderService.class, VexPlaceholderService.class);
    skills.registerQueuedServices();
    PlaceholderService placeholders = skills.require(PlaceholderService.class);
    placeholders.register(SkillPlaceholder.class);

    assertThrows(IllegalStateException.class, () -> placeholders.register(SkillPlaceholder.class));
  }

  @Value
  private static class TestOwner implements ServiceOwner {
    String serviceOwnerName;
  }

  @PlaceholderId("skill")
  @Dependencies
  public static final class SkillPlaceholder implements VexPlaceholder {

    public SkillPlaceholder(final VexServiceRegistry services) { }

    @Override
    public String resolve(final VexPlayer player, final PlaceholderArguments arguments) {
      return String.join(":", arguments.asList());
    }
  }
}
