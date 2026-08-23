package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.PlatformSettingsRequest;
import com.example.PTicketing.dto.response.PlatformSettingsResponse;
import com.example.PTicketing.entity.Event;
import com.example.PTicketing.entity.Order;
import com.example.PTicketing.entity.Payout;
import com.example.PTicketing.entity.Ticket;
import com.example.PTicketing.entity.User;
import com.example.PTicketing.enums.EventStatus;
import com.example.PTicketing.enums.PaymentStatus;
import com.example.PTicketing.enums.PayoutStatus;
import com.example.PTicketing.enums.UserRole;
import com.example.PTicketing.repository.EventRepository;
import com.example.PTicketing.repository.OrderRepository;
import com.example.PTicketing.repository.PayoutRepository;
import com.example.PTicketing.repository.TicketRepository;
import com.example.PTicketing.repository.UserRepository;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.PlatformSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final PayoutRepository payoutRepository;
    private final PlatformSettingsService platformSettingsService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();

        long totalUsers = userRepository.count();
        long totalOrganizers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ORGANIZER).count();
        long totalAttendees = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ATTENDEE).count();
        long totalEvents = eventRepository.count();
        long activeEvents = eventRepository.findByStatus(EventStatus.PUBLISHED).size();

        BigDecimal totalRevenue = orZero(orderRepository.sumTotalByStatus(PaymentStatus.PAID));
        BigDecimal platformFees = orZero(orderRepository.sumFeesByStatus(PaymentStatus.PAID));
        long totalTicketsSold = ticketRepository.count();

        long pendingPayouts = payoutRepository.findByStatus(PayoutStatus.PENDING).size();
        BigDecimal pendingPayoutAmount = orZero(payoutRepository.sumAmountByStatus(PayoutStatus.PENDING));
        BigDecimal totalPaidOut = orZero(payoutRepository.sumAmountByStatus(PayoutStatus.PROCESSED));
        BigDecimal inFlightPayoutAmount = orZero(payoutRepository.sumAmountByStatus(PayoutStatus.PROCESSING))
                .add(orZero(payoutRepository.sumAmountByStatus(PayoutStatus.OTP_PENDING)));

        stats.put("totalUsers", totalUsers);
        stats.put("totalOrganizers", totalOrganizers);
        stats.put("totalAttendees", totalAttendees);
        stats.put("totalEvents", totalEvents);
        stats.put("activeEvents", activeEvents);
        stats.put("totalRevenue", totalRevenue);
        stats.put("platformFees", platformFees);
        stats.put("totalTicketsSold", totalTicketsSold);
        stats.put("pendingPayouts", pendingPayouts);
        stats.put("pendingPayoutAmount", pendingPayoutAmount);
        stats.put("totalPaidOut", totalPaidOut);
        stats.put("inFlightPayoutAmount", inFlightPayoutAmount);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/events")
    public ResponseEntity<List<Map<String, Object>>> getAllEvents() {
        List<Event> events = eventRepository.findAll();
        List<Map<String, Object>> result = events.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", e.getId());
            map.put("title", e.getTitle());
            map.put("slug", e.getSlug());
            map.put("status", e.getStatus());
            map.put("type", e.getType());
            map.put("venue", e.getVenue());
            map.put("startDate", e.getStartDate());
            map.put("endDate", e.getEndDate());
            map.put("flyerImage", e.getFlyerImage());
            map.put("organizerName", e.getOrganizer() != null ? e.getOrganizer().getFullName() : "");
            map.put("organizerId", e.getOrganizer() != null ? e.getOrganizer().getId() : null);
            map.put("bankName", e.getBankName());
            map.put("bankAccountNumber", e.getBankAccountNumber());
            map.put("bankAccountName", e.getBankAccountName());

            List<Order> paidOrders = orderRepository.findByEventId(e.getId()).stream()
                    .filter(o -> o.getPaymentStatus() == PaymentStatus.PAID)
                    .toList();
            BigDecimal revenue = paidOrders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long ticketsSold = paidOrders.stream()
                    .mapToInt(o -> o.getQuantity() != null ? o.getQuantity() : 0)
                    .sum();
            map.put("revenue", revenue);
            map.put("ticketsSold", ticketsSold);
            map.put("orderCount", paidOrders.size());

            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/organizers")
    public ResponseEntity<List<Map<String, Object>>> getAllOrganizers() {
        List<User> organizers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ORGANIZER)
                .toList();

        List<Map<String, Object>> result = organizers.stream().map(o -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", o.getId());
            map.put("fullName", o.getFullName());
            map.put("email", o.getEmail());
            map.put("phone", o.getPhone());
            map.put("createdAt", o.getCreatedAt());
            map.put("bankName", o.getBankName());
            map.put("accountNumber", o.getAccountNumber());
            map.put("accountName", o.getAccountName());

            List<Event> events = eventRepository.findByOrganizerId(o.getId());
            map.put("eventCount", events.size());

            BigDecimal totalEarned = orZero(orderRepository.sumEarnedByOrganizer(o.getId()));
            map.put("totalEarned", totalEarned);

            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/attendees")
    public ResponseEntity<List<Map<String, Object>>> getAllAttendees() {
        List<User> attendees = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ATTENDEE)
                .toList();

        List<Map<String, Object>> result = attendees.stream().map(a -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("fullName", a.getFullName());
            map.put("email", a.getEmail());
            map.put("phone", a.getPhone());
            map.put("createdAt", a.getCreatedAt());

            List<Ticket> tickets = ticketRepository.findByBuyerEmail(a.getEmail());
            map.put("ticketsPurchased", tickets.size());

            long eventsAttended = tickets.stream()
                    .map(t -> t.getEvent().getId())
                    .distinct()
                    .count();
            map.put("eventsAttended", eventsAttended);

            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/payouts")
    public ResponseEntity<List<Map<String, Object>>> getAllPayouts() {
        List<Payout> payouts = payoutRepository.findAll();
        List<Map<String, Object>> result = payouts.stream().map(p -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", p.getId());
            map.put("amount", p.getAmount());
            map.put("bankName", p.getBankName());
            map.put("accountNumber", p.getAccountNumber());
            map.put("accountName", p.getAccountName());
            map.put("status", p.getStatus());
            map.put("reference", p.getReference());
            map.put("failureReason", p.getFailureReason());
            map.put("accountNameMismatch", Boolean.TRUE.equals(p.getAccountNameMismatch()));
            map.put("requestedAt", p.getRequestedAt());
            map.put("processedAt", p.getProcessedAt());
            map.put("userName", p.getUser() != null ? p.getUser().getFullName() : "");
            map.put("userEmail", p.getUser() != null ? p.getUser().getEmail() : "");
            map.put("userId", p.getUser() != null ? p.getUser().getId() : null);
            map.put("feePercentApplied", p.getFeePercentApplied());
            map.put("feeAmount", p.getFeeAmount());
            map.put("netAmount", p.getNetAmount());
            map.put("responseDueAt", p.getResponseDueAt());
            map.put("paystackTransferCode", p.getPaystackTransferCode());
            map.put("otpRequestedAt", p.getOtpRequestedAt());
            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/settings")
    public ResponseEntity<PlatformSettingsResponse> getSettings() {
        return ResponseEntity.ok(platformSettingsService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<PlatformSettingsResponse> updateSettings(
            @Valid @RequestBody PlatformSettingsRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(platformSettingsService.updateSettings(request, admin.getId()));
    }

    private BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
