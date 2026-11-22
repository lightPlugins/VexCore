package dev.vexsoft.core.paper.vexui.template.profession;

import dev.vexsoft.core.paper.vexui.state.VexUIObservable;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateLayout;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateText;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;

/** Viewer/profile-bound adapter to the caller's services, publications and localization. */
public interface VexUIProfessionProvider extends VexUIObservable<VexUIProfessionSnapshot>, VexUITemplateText,
    AutoCloseable {

    /** Returns the layout of a profession menu section. */
    VexUITemplateLayout layout(String section);

    /** Returns the namespaced scene font prefix, such as isles:professions/mining, or null for the standard workspace. */
    default String sceneTheme(final String profession) {
        return null;
    }

    /** Whether each scene's three bitmap strips are also registered in the GUI atlas as panorama_0 through panorama_2. */
    default boolean hasPanoramaSprites() {
        return false;
    }

    /** Claims the selected milestone and returns whether its rewards were collected. */
    CompletionStage<Boolean> claim(String profession, int level);

    /** Expands complete localized tooltip lines, preserving styled whole-line sections. */
    List<Component> lore(String key, Map<String, String> values, Map<String, List<Component>> sections);

    /** Releases viewer-owned adapter resources without cancelling authoritative domain operations. */
    @Override
    void close();
}
