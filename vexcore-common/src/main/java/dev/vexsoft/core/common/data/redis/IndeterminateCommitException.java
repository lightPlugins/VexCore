package dev.vexsoft.core.common.data.redis;

/** A timed-out operation may already be committed; its session must reload rather than compensate blindly. */
public final class IndeterminateCommitException extends IllegalStateException {

    public IndeterminateCommitException(final Throwable cause) {
        super("Redis could not confirm the operation outcome; reconnect to reload authoritative state", cause);
    }
}
