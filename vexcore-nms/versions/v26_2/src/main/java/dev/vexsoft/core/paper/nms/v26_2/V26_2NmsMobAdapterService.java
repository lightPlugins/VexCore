package dev.vexsoft.core.paper.nms.v26_2;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.nms.goal.NmsLookAtPlayerSpec;
import dev.vexsoft.core.paper.nms.goal.NmsOwnerMeleeSpec;
import dev.vexsoft.core.paper.nms.goal.NmsRandomMovementSpec;
import dev.vexsoft.core.paper.nms.service.NmsMobAdapterService;
import dev.vexsoft.core.paper.nms.v26_2.goal.V26_2LookAtPlayerGoal;
import dev.vexsoft.core.paper.nms.v26_2.goal.V26_2OwnerMeleeGoal;
import dev.vexsoft.core.paper.nms.v26_2.goal.V26_2RandomMovementGoal;
import java.lang.reflect.Field;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.npc.villager.Villager;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.entity.Mob;

/** Minecraft 26.2 implementation of native mob control. */
@Dependencies
public final class V26_2NmsMobAdapterService implements NmsMobAdapterService {

    private static final Field GOAL_SELECTOR = selectorField("goalSelector");
    private static final Field TARGET_SELECTOR = selectorField("targetSelector");

    public V26_2NmsMobAdapterService(final VexServiceRegistry services) {
        Objects.requireNonNull(services, "services");
    }

    @Override
    public void neutralize(final Mob mob) {
        CraftMob craftMob = handle(mob);

        craftMob.getHandle().getNavigation().stop();
        selector(craftMob, GOAL_SELECTOR).removeAllGoals(goal -> true);
        selector(craftMob, TARGET_SELECTOR).removeAllGoals(goal -> true);
        clearVanillaBrain(craftMob);
        craftMob.getHandle().setTarget(null);
        craftMob.getHandle().setNoAi(true);
        mob.setAware(false);
        mob.setTarget(null);
    }

    @Override
    public void addRandomMovement(final Mob mob, final NmsRandomMovementSpec spec) {
        CraftMob craftMob = handle(mob);

        selector(craftMob, GOAL_SELECTOR).addGoal(
            Objects.requireNonNull(spec, "spec").priority(),
            new V26_2RandomMovementGoal(craftMob.getHandle(), mob, mob.getPathfinder(), spec)
        );
    }

    @Override
    public void addLookAtPlayer(final Mob mob, final NmsLookAtPlayerSpec spec) {
        CraftMob craftMob = handle(mob);

        selector(craftMob, GOAL_SELECTOR).addGoal(
            Objects.requireNonNull(spec, "spec").priority(),
            new V26_2LookAtPlayerGoal(craftMob.getHandle(), mob, mob.getPathfinder(), spec)
        );
    }

    @Override
    public void activateGoals(final Mob mob) {
        // Initializers may rebuild a mob's brain. Clear it after initialization as well,
        // before enabling the native tick needed by our explicitly installed goals.
        clearVanillaBrain(handle(mob));
        handle(mob).getHandle().setNoAi(false);
        mob.setAware(true);
    }

    @Override
    public void addOwnerMelee(final Mob mob, final NmsOwnerMeleeSpec specification) {
        NmsOwnerMeleeSpec checkedSpecification = Objects.requireNonNull(specification, "specification");

        selector(handle(mob), GOAL_SELECTOR).addGoal(
            checkedSpecification.priority(),
            new V26_2OwnerMeleeGoal(mob, checkedSpecification)
        );
    }

    @Override
    public void deactivateGoals(final Mob mob) {
        neutralize(mob);
    }

    private static CraftMob handle(final Mob mob) {
        if (!(Objects.requireNonNull(mob, "mob") instanceof CraftMob craftMob)) {
            throw new IllegalArgumentException("Unsupported mob implementation: " + mob.getClass());
        }

        return craftMob;
    }

    @SuppressWarnings("unchecked") // The brain belongs to this exact entity; Bukkit erases its subtype.
    private static void clearVanillaBrain(final CraftMob mob) {
        Brain<LivingEntity> brain = (Brain<LivingEntity>) mob.getHandle().getBrain();

        if (mob.getHandle().level() instanceof ServerLevel level) {
            brain.stopAll(level, mob.getHandle());
        }

        brain.removeAllBehaviors();
        brain.clearMemories();
        mob.getHandle().getNavigation().stop();

        if (mob.getHandle() instanceof Villager villager) {
            // 26.2 tracks appearance finalization separately from profession/type setters.
            villager.setVillagerDataFinalized(true);
        }
    }

    private static GoalSelector selector(final CraftMob mob, final Field field) {
        try {
            return (GoalSelector) field.get(mob.getHandle());
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access native mob goal selector", exception);
        }
    }

    private static Field selectorField(final String name) {
        try {
            Field field = Class.forName("net.minecraft.world.entity.Mob").getDeclaredField(name);

            field.setAccessible(true);

            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to resolve native mob field " + name, exception);
        }
    }
}
