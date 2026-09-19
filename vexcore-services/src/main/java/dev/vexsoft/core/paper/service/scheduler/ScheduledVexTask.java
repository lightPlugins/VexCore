package dev.vexsoft.core.paper.service.scheduler;

import dev.vexsoft.core.paper.scheduler.VexTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.Getter;

/** Adapts a platform task and releases its owner's tracking entry when cancelled. */
public final class ScheduledVexTask implements VexTask {

    @Getter
    private final ScheduledTask task;
    private final Consumer<ScheduledTask> cancelled;

    public ScheduledVexTask(final ScheduledTask task) {
        this(task, ignored -> {
        });
    }

    public ScheduledVexTask(final ScheduledTask task, final Consumer<ScheduledTask> cancelled) {
        this.task = Objects.requireNonNull(task, "task");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
    }

    @Override
    public void cancel() {
        task.cancel();
        cancelled.accept(task);
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
