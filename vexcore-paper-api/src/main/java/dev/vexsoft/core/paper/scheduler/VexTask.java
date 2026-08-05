package dev.vexsoft.core.paper.scheduler;

/** Represents an owner-bound scheduled task whose state mirrors Paper's scheduler handle. */
public interface VexTask {

  /** Cancels this task when it has not completed yet */
  void cancel();

  /** @return {@code true} if this task has been cancelled */
  boolean isCancelled();

  /** @return {@code true} if this task completed its final execution */
  boolean isFinished();

  /** @return {@code true} if this task is scheduled repeatedly */
  boolean isRepeating();
}
