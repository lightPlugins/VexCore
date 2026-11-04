package dev.vexsoft.core.paper.vexui.template.shop;

import dev.vexsoft.core.paper.vexui.template.VexUITemplateProvider;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;

/** Shop adapter; implementations validate and commit every purchase through their authoritative domain service. */
public interface VexUIShopProvider extends VexUITemplateProvider<VexUIShopTemplate.Snapshot> {

    /** Returns a current presentation quote; it never reserves funds or grants an item. */
    Quote quote(String product, int quantity);

    /** Revalidates funds, capacity, stock, and identity atomically; completes with localized result feedback. */
    CompletionStage<Component> purchase(String product, int quantity);

    /** A disabled quote supplies the reason as the purchase button's label. */
    record Quote(Component summary, Component button, boolean enabled) {
        /** Requires localized feedback for both enabled and disabled purchase states. */
        public Quote {
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(button, "button");
        }
    }
}
