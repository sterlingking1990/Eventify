package com.example.PTicketing.enums;

public enum PayoutStatus {
    /** Requested by the organiser, not yet acted on. Funds are reserved against their balance. */
    PENDING,

    /**
     * Handed to the payment provider, outcome not yet known.
     *
     * <p>A bank transfer is asynchronous: the provider accepts it, then confirms or
     * fails minutes later. Without this state a payout would appear settled the
     * instant it was submitted, and a later failure would have nothing to correct.
     * Funds stay reserved while in this state.
     */
    PROCESSING,

    /** Money has left. Terminal. */
    PROCESSED,

    /** Rejected by an admin, or the transfer failed. Funds return to the balance. */
    FAILED
}
