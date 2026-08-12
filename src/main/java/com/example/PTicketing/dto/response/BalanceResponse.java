package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class BalanceResponse {

    /** Withdrawable now: released earnings, less anything already requested or paid. */
    private BigDecimal availableBalance;

    /**
     * Earned but still inside its hold — money from events that have not finished,
     * or finished too recently. Not withdrawable, but the organiser should see it
     * rather than wonder where their sales went.
     */
    private BigDecimal heldBalance;

    /** When the next held tranche unlocks. Null when nothing is held. */
    private LocalDateTime nextReleaseAt;

    /** Requested and awaiting processing. Already deducted from availableBalance. */
    private BigDecimal pendingPayouts;

    /** Everything sold, before any hold or payout. */
    private BigDecimal totalEarned;

    /** Paid out historically. */
    private BigDecimal totalPaidOut;
}
