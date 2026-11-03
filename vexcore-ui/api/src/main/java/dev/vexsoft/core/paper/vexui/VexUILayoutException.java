package dev.vexsoft.core.paper.vexui;

import lombok.Getter;

/** Layout validation failure identifying the offending occurrence in the nested element tree. */
public final class VexUILayoutException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;
    @Getter
    private final String path;

    /** Attaches a developer-readable tree path while preserving the original validation failure. */
    public VexUILayoutException(final String path, final IllegalArgumentException cause) {
        super(path + ": " + cause.getMessage(), cause);
        this.path = path;
    }
}
