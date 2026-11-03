package dev.vexsoft.core.paper.vexui.template;

import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;

/** Supplies upgrade presentations and delegates execution to the plugin's authoritative upgrade transaction. */
public interface VexUIUpgradeProvider extends VexUITemplateProvider<VexUIUpgradeTemplate.Snapshot> {

    /** Revalidates the item, requirements, payment and compensation before performing this selected upgrade. */
    CompletionStage<Component> upgrade(String option);
}
