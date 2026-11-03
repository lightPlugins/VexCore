package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.paper.vexui.VexUIObservable;
import dev.vexsoft.core.paper.vexui.VexUISession;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;

/** Viewer-bound station adapter; inventories, costs, requirements and grants remain domain-owned. */
public interface VexUICraftingProvider extends VexUIObservable<VexUICraftingSnapshot>, VexUITemplateText, AutoCloseable {

    /** Returns geometry for categories, recipes or costs. */
    VexUITemplateLayout layout(String section);

    /** Resolves one visible recipe without scanning maximum craft quantities. */
    VexUICraftingSnapshot.Recipe recipe(String id);

    /** Freezes scaled costs and an authoritative token for the exact requested executions. */
    VexUICraftingQuote quote(String id, WholeAmount executions);

    /** Finds the exact currently affordable and deliverable quantity; invoked only by the Max action. */
    WholeAmount maximum(String id);

    /** Revalidates the frozen quote and returns localized completion feedback without reducing its quantity. */
    CompletionStage<Component> craft(String id, VexUICraftingQuote shown);

    /** Returns whether this material has an inspectable child recipe. */
    default boolean craftable(final VexUICraftingQuote.Cost cost) {
        return false;
    }

    /** Navigates to the material's recipe while the current quantity and page remain retained. */
    default void openIngredient(final VexUISession session, final String parentRecipe,
                                final VexUICraftingQuote.Cost cost) {
    }

    /** Releases viewer subscriptions without cancelling an already submitted domain transaction. */
    @Override
    void close();
}
