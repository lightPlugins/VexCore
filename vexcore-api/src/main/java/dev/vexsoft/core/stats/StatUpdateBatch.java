package dev.vexsoft.core.stats;

/** Defers recalculation until a group of changes has completed. */
public interface StatUpdateBatch extends AutoCloseable {

  /** Finishes the batch and recalculates every changed stat once. */
  @Override
  void close();
}
