package dev.vexsoft.core.paper.vexui.runtime;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/** Deterministic queued scheduler instantiated by the real scoped registry in lifecycle tests. */
@Dependencies
public final class VexUITestScheduleService implements ScheduleService, AutoCloseable {

    private final Deque<Job> jobs = new ArrayDeque<>();
    private final Plugin owner;
    Runnable beforeNextTask;

    public VexUITestScheduleService(final VexServiceRegistry services) {
        owner = services.getOwner() instanceof Plugin plugin ? plugin : null;
    }

    public void flush() {
        int count = 0;
        while (!jobs.isEmpty()) {
            if (++count > 1000) {
                throw new AssertionError("Unbounded task loop");
            }
            Job job = jobs.removeFirst();
            if (!job.isCancelled()) {
                job.action.run();
                job.finished = true;
            }
        }
    }

    public int queuedTaskCount() {
        return jobs.size();
    }

    private VexTask enqueue(final Runnable action) {
        if (beforeNextTask != null) {
            var callback = beforeNextTask;
            beforeNextTask = null;
            callback.run();
        }
        if (owner != null && !owner.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
        }
        Job job = new Job(action);
        jobs.addLast(job);
        return job;
    }

    @Override
    public Plugin getOwner() {
        return owner;
    }

    @Override
    public VexTask runGlobal(final Runnable task) {
        return enqueue(task);
    }

    @Override
    public VexTask runGlobalLater(final long delay, final Runnable task) {
        return enqueue(task);
    }

    @Override
    public VexTask runGlobalTimer(final long initial, final long period, final Runnable task) {
        return enqueue(task);
    }

    @Override
    public VexTask runAt(final Location location, final Runnable task) {
        return enqueue(task);
    }

    @Override
    public VexTask runAtLater(final Location location, final long delay, final Runnable task) {
        return enqueue(task);
    }

    @Override
    public VexTask runAtTimer(final Location location, final long initial, final long period, final Runnable task) {
        return enqueue(task);
    }

    @Override
    public Optional<VexTask> runFor(final Entity entity, final Runnable task) {
        return Optional.of(enqueue(task));
    }

    @Override
    public Optional<VexTask> runFor(final Entity entity, final Runnable task, final Runnable retired) {
        return Optional.of(enqueue(task));
    }

    @Override
    public Optional<VexTask> runForLater(final Entity entity, final long delay, final Runnable task, final Runnable retired) {
        return Optional.of(enqueue(task));
    }

    @Override
    public Optional<VexTask> runForTimer(final Entity entity, final long initial, final long period,
                                      final Runnable task, final Runnable retired) {
        return Optional.of(enqueue(task));
    }

    @Override
    public VexTask runAsync(final Runnable task) {
        return enqueue(task);
    }

    @Override
    public VexTask runAsyncLater(final Duration delay, final Runnable task) {
        return enqueue(task);
    }

    @Override
    public VexTask runAsyncTimer(final Duration initial, final Duration interval, final Runnable task) {
        return enqueue(task);
    }

    @Override
    public void cancelAll() {
        jobs.forEach(Job::cancel);
        jobs.clear();
    }

    @Override
    public void close() {
        cancelAll();
    }

    @Getter
    private static final class Job implements VexTask {
        private final Runnable action;
        private boolean cancelled;
        private boolean finished;

        private Job(final Runnable action) {
            this.action = action;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isRepeating() {
            return false;
        }
    }
}
