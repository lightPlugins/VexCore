package dev.vexsoft.core.paper.packets.dummy;

import java.util.Objects;

/** Signed texture property, including the skin-model data encoded by Minecraft. */
public record SkinTexture(String value, String signature) {

    /** Requires the encoded texture value; the signature may be absent. */
    public SkinTexture {
        Objects.requireNonNull(value, "value");
    }
}
