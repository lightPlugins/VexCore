package dev.vexsoft.core.paper.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;

public final class ScheduledVexTask implements VexTask {
  private final ScheduledTask task;

  public ScheduledVexTask(ScheduledTask task) {
    this.task = Objects.requireNonNull(task, "task");
  }

  @Override
  public void cancel() {
    task.cancel();
  }

  @Override
  public boolean isCancelled() {
    return task.isCancelled();
  }

  @Override
  public boolean isFinished() {
    return task.getExecutionState() == ScheduledTask.ExecutionState.FINISHED;
  }

  @Override
  public boolean isRepeating() {
    return task.isRepeatingTask();
  }

  public ScheduledTask paperTask() {
    return task;
  }
}
