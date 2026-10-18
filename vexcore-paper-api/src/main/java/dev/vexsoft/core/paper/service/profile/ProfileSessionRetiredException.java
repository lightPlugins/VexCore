package dev.vexsoft.core.paper.service.profile;

/** Signals that the player entity required for a profile operation has retired or disconnected. */
public final class ProfileSessionRetiredException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** Creates the lifecycle rejection; callers must preserve persisted work for profile recovery. */
    public ProfileSessionRetiredException() {
        super("Player entity retired");
    }
}
