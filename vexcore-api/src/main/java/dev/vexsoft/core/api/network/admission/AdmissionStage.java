package dev.vexsoft.core.api.network.admission;

/** Network-authoritative stages of a queued game entry. */
public enum AdmissionStage {
    WAITING,
    RESERVED,
    TRANSFERRING,
    ARRIVED,
    EXPIRED
}
