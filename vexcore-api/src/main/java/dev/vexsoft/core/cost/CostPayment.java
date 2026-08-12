package dev.vexsoft.core.cost;

import java.util.List;

/** Reversible result of consuming a complete compiled cost section. */
public record CostPayment(
    boolean successful,
    CostExecutionResult result,
    List<Entry> entries
) {

  /** Copies all receipts captured during payment. */
  public CostPayment {
    entries = List.copyOf(entries);
  }

  /** Associates one consumed cost implementation with its refund receipt. */
  public record Entry(CompiledCost cost, CostReceipt receipt) {}
}
