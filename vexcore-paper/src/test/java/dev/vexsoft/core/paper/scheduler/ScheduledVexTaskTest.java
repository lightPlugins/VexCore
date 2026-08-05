package dev.vexsoft.core.paper.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledVexTaskTest {

  @Test
  void delegatesTaskStateAndCancellation() {
    TestScheduledTask scheduled = new TestScheduledTask(true);
    VexTask task = new ScheduledVexTask(scheduled);

    assertTrue(task.isRepeating());
    assertFalse(task.isCancelled());
    assertFalse(task.isFinished());

    task.cancel();

    assertTrue(task.isCancelled());
  }

  @Test
  void reportsFinishedTasks() {
    TestScheduledTask scheduled = new TestScheduledTask(false);
    scheduled.state = ScheduledTask.ExecutionState.FINISHED;

    assertTrue(new ScheduledVexTask(scheduled).isFinished());
  }

  private static final class TestScheduledTask implements ScheduledTask {
    private final boolean repeating;
    private ExecutionState state = ExecutionState.IDLE;

    private TestScheduledTask(boolean repeating) {
      this.repeating = repeating;
    }

    @Override
    public Plugin getOwningPlugin() {
      throw new UnsupportedOperationException("Owning plugin is not used by this test double");
    }

    @Override
    public boolean isRepeatingTask() {
      return repeating;
    }

    @Override
    public CancelledState cancel() {
      state = ExecutionState.CANCELLED;
      return CancelledState.CANCELLED_BY_CALLER;
    }

    @Override
    public ExecutionState getExecutionState() {
      return state;
    }
  }
}
