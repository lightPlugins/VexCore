package dev.vexsoft.core.paper.scheduler;

public interface VexTask {

  /** Cancels this task when it has not completed yet */
  public void cancel();

  /** Checks whether this task has been cancelled */
  public boolean isCancelled();

  /** Checks whether this task has completed its final execution */
  public boolean isFinished();

  /** Checks whether this task runs repeatedly */
  public boolean isRepeating();
}
