package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.paper.vexui.VexUIObservable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;

/** Viewer/profile-bound adapter to the caller's services, publications and localization. */
public interface VexUIProfessionProvider extends VexUIObservable<VexUIProfessionSnapshot>, VexUITemplateText,
    AutoCloseable {

    /** Returns independently reloadable geometry for main, job, levels, rewards, sources, unlocks or receipt. */
    VexUITemplateLayout layout(String section);

    /** Claims one milestone, a complete profession when level is null, or all professions when both are null. */
    CompletionStage<VexUIProfessionSnapshot.Claim> claim(String profession, Integer level);

    /** Expands complete localized tooltip lines, preserving styled whole-line sections. */
    List<Component> lore(String key, Map<String, String> values, Map<String, List<Component>> sections);

    /** Marks only the actually displayed unlock revisions as read. */
    CompletionStage<Void> seen(List<String> unlocks);

    /** Releases viewer-owned adapter resources without cancelling authoritative domain operations. */
    @Override
    void close();
}
