package dev.vexsoft.core.requirement;

/** Non-mutating requirement outcome. */
public record RequirementResult(boolean satisfied, String message) {

  /** Creates a satisfied result. */
  public static RequirementResult success() {
    return new RequirementResult(true, "");
  }

  /** Creates an unsatisfied result. */
  public static RequirementResult missing(final String message) {
    return new RequirementResult(false, message);
  }
}
