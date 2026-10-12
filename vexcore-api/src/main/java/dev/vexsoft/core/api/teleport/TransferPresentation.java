package dev.vexsoft.core.api.teleport;

/** Serialized loading/error Components carried to a backend without the initiating plugin. */
public record TransferPresentation(String loadingTitle, String failedTitle, String color) {

    /** Validates bounded presentation data and an Adventure bossbar color name. */
    public TransferPresentation {
        if (loadingTitle == null || failedTitle == null || loadingTitle.length() > 8192
            || failedTitle.length() > 8192 || color == null
            || !color.matches("PINK|BLUE|RED|GREEN|YELLOW|PURPLE|WHITE")) {
            throw new IllegalArgumentException("Invalid transfer presentation");
        }
    }
}
