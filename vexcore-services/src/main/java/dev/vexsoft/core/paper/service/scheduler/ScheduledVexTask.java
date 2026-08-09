package dev.vexsoft.core.paper.service.scheduler;

import dev.vexsoft.core.paper.scheduler.VexTask;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ScheduledVexTask implements VexTask {
  @Getter
  @NonNull
  private final ScheduledTask task;

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

}
