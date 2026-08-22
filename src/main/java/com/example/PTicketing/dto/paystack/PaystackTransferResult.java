package com.example.PTicketing.dto.paystack;

/**
 * Outcome of a Paystack transfer initiate/finalize/resend-OTP call.
 *
 * <p>{@code status} is one of Paystack's own values ("otp", "pending", "success",
 * "failed") or the synthetic "invalid_otp" — used when finalize_transfer rejects the
 * OTP itself (wrong or expired) rather than the transfer failing, so the caller can
 * leave the payout in OTP_PENDING and let the admin retry instead of failing it.
 */
public record PaystackTransferResult(String status, String transferCode, String reference, String message) {
}
