package dev.vexsoft.core.api.signal;

/**
 * Summarizes a completed synchronous signal dispatch.
 *
 * @param delivered number of listeners that completed successfully
 * @param failed number of listeners that threw an exception
 */
public record SignalDispatchResult(int delivered, int failed) {

  /** Returns the total number of listeners invoked during dispatch. */
  public int getListenerCount() {
    return delivered + failed;
  }

  /** Returns whether every invoked listener completed successfully. */
  public boolean isSuccessful() {
    return failed == 0;
  }
}
