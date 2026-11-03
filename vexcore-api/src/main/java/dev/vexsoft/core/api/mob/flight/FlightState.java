package dev.vexsoft.core.api.mob.flight;



/** Result of the active flight order, observed on the carrier's owning thread. */
public enum FlightState {
    MOVING,
    ARRIVED,
    BLOCKED
}
