package dev.vexsoft.core.paper.service.profile;

/** Rejects a concurrent operation before its mutation runs while an existing profile operation is pending. */
public final class ProfileSessionBusyException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** Creates the transient rejection for a session already processing another operation. */
    public ProfileSessionBusyException() {
        super("Profile session is busy");
    }
}
