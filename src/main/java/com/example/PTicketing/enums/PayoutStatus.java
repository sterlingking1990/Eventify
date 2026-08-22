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
    FAILED,

    /**
     * The provider requires a one-time PIN before the transfer will complete.
     *
     * <p>Paystack sends this OTP out-of-band to the account holder (Brandible), not
     * to Eventify — submitting it back is the second authorization required before
     * money actually moves. Distinct from PROCESSING (whose outcome is unknown but
     * needs no human action) because this state needs someone to act right now.
     * Funds stay reserved while in this state.
     */
    OTP_PENDING
}
