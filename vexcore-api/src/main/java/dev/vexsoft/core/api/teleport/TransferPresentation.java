package dev.vexsoft.core.api.teleport;

/** Serialized loading/error Components and arrival audio carried to the destination backend. */
public record TransferPresentation(String loadingTitle, String failedTitle, String color, Sound arrivalSound) {

    /** Validates bounded presentation data and an Adventure bossbar color name. */
    public TransferPresentation {
        if (loadingTitle == null || failedTitle == null || loadingTitle.length() > 8192
            || failedTitle.length() > 8192 || color == null
            || !color.matches("PINK|BLUE|RED|GREEN|YELLOW|PURPLE|WHITE")) {
            throw new IllegalArgumentException("Invalid transfer presentation");
        }
    }

    /** Preserves presentations without arrival audio and older serialized transfer tickets. */
    public TransferPresentation(final String loadingTitle, final String failedTitle, final String color) {
        this(loadingTitle, failedTitle, color, null);
    }

    /** Validated arrival audio; volume zero silences it without changing the transfer presentation. */
    public record Sound(String type, float volume, float pitch) {

        /** Validates the namespaced sound ID and supported volume and pitch ranges. */
        public Sound {
            if (type == null || !type.matches("[a-z0-9._-]+:[a-z0-9/._-]+")
                || !Float.isFinite(volume) || volume < 0 || volume > 10
                || !Float.isFinite(pitch) || pitch <= 0 || pitch > 2) {
                throw new IllegalArgumentException("Invalid transfer arrival sound");
            }
        }
    }
}
