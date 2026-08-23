package com.example.PTicketing.service;

import com.example.PTicketing.entity.Payout;
import com.example.PTicketing.entity.User;
import com.example.PTicketing.enums.UserRole;
import com.example.PTicketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tells an organiser what happened to their cashout, and tells admins when one has
 * gone unanswered too long.
 *
 * <p>Fire-and-forget, same as {@link EventNotificationService}: a payout has already
 * changed state by the time these run, so a mail problem must never unwind it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutNotificationService {

    private final EmailService emailService;
    private final UserRepository userRepository;

    @Async
    public void notifyApproved(Payout payout) {
        send(payout, "Your cashout request is being processed",
                "Hi " + name(payout) + ",\n\n" +
                "Your cashout request " + payout.getReference() + " for " +
                money(payout.getNetAmount()) + " has been approved and is being sent to your bank account.\n\n" +
                "We'll let you know as soon as it's complete.\n\n" +
                "— Eventify");
    }

    @Async
    public void notifyRejected(Payout payout) {
        send(payout, "Your cashout request was not approved",
                "Hi " + name(payout) + ",\n\n" +
                "Your cashout request " + payout.getReference() + " for " +
                money(payout.getAmount()) + " was not approved.\n\n" +
                "Reason: " + payout.getFailureReason() + "\n\n" +
                "You can submit a new request once the issue above is resolved.\n\n" +
                "— Eventify");
    }

    @Async
    public void notifyDisbursed(Payout payout) {
        send(payout, "Your cashout has been paid",
                "Hi " + name(payout) + ",\n\n" +
                money(payout.getNetAmount()) + " has been sent to " + payout.getBankName() +
                " account " + payout.getAccountNumber() + " (reference " + payout.getReference() + ").\n\n" +
                "— Eventify");
    }

    @Async
    public void notifyDisbursementFailed(Payout payout) {
        send(payout, "Your cashout could not be completed",
                "Hi " + name(payout) + ",\n\n" +
                "We were unable to complete cashout request " + payout.getReference() + " for " +
                money(payout.getAmount()) + ".\n\n" +
                "Reason: " + payout.getFailureReason() + "\n\n" +
                "The funds remain in your account balance — please submit a new request, " +
                "or contact support if this keeps happening.\n\n" +
                "— Eventify");
    }

    @Async
    public void notifySlaBreach(List<Payout> overduePayouts) {
        if (overduePayouts.isEmpty()) return;

        List<String> adminEmails = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .toList();

        if (adminEmails.isEmpty()) {
            log.warn("{} payout(s) breached their response SLA but no admin has an email to notify",
                    overduePayouts.size());
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append("The following cashout request(s) have not been approved or rejected within their SLA:\n\n");
        for (Payout p : overduePayouts) {
            body.append("- ").append(p.getReference())
                    .append(" — ").append(money(p.getAmount()))
                    .append(", requested ").append(p.getRequestedAt())
                    .append(", due by ").append(p.getResponseDueAt())
                    .append("\n");
        }
        body.append("\nPlease review them in the admin panel.\n");

        String subject = overduePayouts.size() == 1
                ? "1 cashout request is overdue"
                : overduePayouts.size() + " cashout requests are overdue";

        for (String email : adminEmails) {
            try {
                emailService.sendSimpleEmail(email, subject, body.toString());
            } catch (Exception e) {
                log.error("Failed to send SLA breach notice to {}: {}", email, e.getMessage());
            }
        }
    }

    @Async
    public void notifyOtpStale(List<Payout> stalePayouts) {
        if (stalePayouts.isEmpty()) return;

        List<String> adminEmails = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .toList();

        if (adminEmails.isEmpty()) {
            log.warn("{} payout(s) are stuck awaiting an OTP but no admin has an email to notify",
                    stalePayouts.size());
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append("The following transfers have been waiting for their Paystack OTP ")
            .append("longer than expected:\n\n");
        for (Payout p : stalePayouts) {
            body.append("- ").append(p.getReference())
                    .append(" — ").append(money(p.getNetAmount()))
                    .append(", approved ").append(p.getProcessedAt())
                    .append("\n");
        }
        body.append("\nThe organiser's funds stay reserved while these hang. Enter the OTP ")
            .append("(if it can still arrive), resend it, or force-fail the payout in the ")
            .append("admin panel.\n");

        String subject = stalePayouts.size() == 1
                ? "1 transfer is stuck waiting on an OTP"
                : stalePayouts.size() + " transfers are stuck waiting on an OTP";

        for (String email : adminEmails) {
            try {
                emailService.sendSimpleEmail(email, subject, body.toString());
            } catch (Exception e) {
                log.error("Failed to send stale-OTP notice to {}: {}", email, e.getMessage());
            }
        }
    }

    /**
     * Tells admins a cashout's destination account does not resemble the organiser
     * before anyone approves it — the cheapest moment to catch a compromised login.
     */
    @Async
    public void notifyNameMismatch(Payout payout) {
        List<String> adminEmails = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .toList();

        if (adminEmails.isEmpty()) return;

        String body = "Cashout request " + payout.getReference() + " for "
                + money(payout.getAmount()) + " is flagged:\n\n"
                + "Organiser: " + name(payout) + "\n"
                + "Destination account: " + payout.getAccountName()
                + " (" + payout.getBankName() + " " + payout.getAccountNumber() + ")\n"
                + (Boolean.TRUE.equals(payout.getAccountNameMismatch())
                        ? "The account holder's name shares no token with the organiser's signup name,\n"
                          + "or the account was not verified with a bank code.\n"
                        : "") + "\n"
                + "Review it in the admin panel before approving.\n";

        for (String email : adminEmails) {
            try {
                emailService.sendSimpleEmail(email,
                        "Cashout flagged: account name mismatch", body);
            } catch (Exception e) {
                log.error("Failed to send name-mismatch notice to {}: {}", email, e.getMessage());
            }
        }
    }

    private void send(Payout payout, String subject, String body) {
        User user = payout.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Payout {} has no organiser email to notify", payout.getReference());
            return;
        }
        try {
            emailService.sendSimpleEmail(user.getEmail(), subject, body);
        } catch (Exception e) {
            log.error("Failed to send '{}' to {} for payout {}: {}",
                    subject, user.getEmail(), payout.getReference(), e.getMessage());
        }
    }

    private String name(Payout payout) {
        return payout.getUser() != null && payout.getUser().getFullName() != null
                ? payout.getUser().getFullName() : "there";
    }

    private String money(java.math.BigDecimal amount) {
        return amount != null ? "₦" + amount.toPlainString() : "an unknown amount";
    }
}
