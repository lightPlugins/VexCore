package dev.vexsoft.core.api.teleport;

import dev.vexsoft.core.api.network.admission.AdmissionReference;

/** Optional fenced admission and explicit account-only handover policy. */
public record TransferOptions(
    AdmissionReference admission, TransferPresentation presentation, boolean accountOnly
) {

    /** Preserves ordinary teleports without routing admission or profile retirement policy changes. */
    public static TransferOptions ordinary() {
        return new TransferOptions(null, null, false);
    }
}
