package com.example.PTicketing.service;

import com.example.PTicketing.config.PaystackConfig;
import com.example.PTicketing.dto.request.InitializeOrderRequest;
import com.example.PTicketing.dto.response.OrderResponse;
import com.example.PTicketing.dto.response.TicketResponse;
import com.example.PTicketing.entity.*;
import com.example.PTicketing.enums.*;
import com.example.PTicketing.exception.BadRequestException;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PaystackService paystackService;
    private final EmailService emailService;
    private final DiscountService discountService;
    private final ReferralService referralService;
    private final PaystackConfig paystackConfig;
    private final PricingService pricingService;
    private final QrSigningService qrSigningService;
    // One-way dependency: IntegrationOrderService does not reference this class, so
    // there is no bean cycle. Used only to reuse its channel-delivery path.
    private final IntegrationOrderService integrationOrderService;

    /**
     * How long a web order holds its seats before they go back on sale. Shorter
     * than the WhatsApp hold because card checkout completes in a browser session
     * rather than waiting on a bank transfer.
     */
    private static final int WEB_HOLD_MINUTES = 15;

    /** Kept in step with the external-channel path; see IntegrationOrderService. */
    @Value("${integration.payout-hold-hours:24}")
    private int payoutHoldHours;

    @Transactional
    public OrderResponse initializeOrder(InitializeOrderRequest request, Long userId) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BadRequestException("Event is not available for purchase");
        }

        TicketType ticketType = ticketTypeRepository.findById(request.getTicketTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found"));

        if (!ticketType.getEvent().getId().equals(event.getId())) {
            throw new BadRequestException("Ticket type does not belong to this event");
        }

        // Reserve the seats now rather than at payment time. Two reasons:
        //
        //  1. A read-then-write availability check loses updates when two buyers
        //     confirm at once, which is exactly what a popular event produces.
        //     Pushing the check into the UPDATE's WHERE clause lets the database
        //     serialise it.
        //  2. It matches the external-channel path, so confirmation never has to
        //     decide whether stock was already taken — it never is.
        //
        // Unpaid holds are returned by IntegrationOrderService.releaseExpiredHolds.
        int reserved = ticketTypeRepository.reserveQuantity(ticketType.getId(), request.getQuantity());
        if (reserved == 0) {
            int available = ticketType.getQuantity() - ticketType.getTicketsSold();
            throw new BadRequestException(available <= 0
                    ? "This ticket type is sold out"
                    : "Not enough tickets available. Only " + available + " left");
        }

        BigDecimal subtotal = ticketType.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (request.getDiscountCode() != null && !request.getDiscountCode().isBlank()) {
            discountService.validateAndApply(request.getDiscountCode(), event.getId());
            discountAmount = discountService.calculateDiscount(
                    request.getDiscountCode(), event.getId(), subtotal
            );
        }

        BigDecimal afterDiscount = subtotal.subtract(discountAmount);
        BigDecimal feeAmount = calculateFee(event.getType(), afterDiscount, request.getQuantity());
        BigDecimal totalAmount = afterDiscount.add(feeAmount);

        String orderRef = "PTK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        PaymentMethod method = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.PAYSTACK;

        // Manual bank transfer paid organisers directly, outside Paystack and the
        // ledger: no platform fee, no release hold, no cashout trail — and it put
        // personal bank accounts on public event pages. All money now flows through
        // the central Paystack account; the enum survives for historical orders.
        if (method == PaymentMethod.TRANSFER) {
            throw new BadRequestException(
                    "Direct bank transfer is no longer supported. Please pay via Paystack.");
        }

        Order order = Order.builder()
                .orderRef(orderRef)
                .event(event)
                .subtotal(ticketType.isFree() ? BigDecimal.ZERO : subtotal)
                .feeAmount(feeAmount)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .paymentMethod(method)
                .paymentStatus(PaymentStatus.PENDING)
                .currency("NGN")
                .buyerEmail(request.getBuyerEmail())
                .buyerName(request.getBuyerName())
                .discountCode(request.getDiscountCode())
                .referralCode(request.getReferralCode())
                .ticketTypeId(request.getTicketTypeId())
                .quantity(request.getQuantity())
                .sourceChannel("web")
                .holdExpiresAt(LocalDateTime.now().plusMinutes(WEB_HOLD_MINUTES))
                // Same hold rule as the external channels — the organiser's share is
                // released a fixed period after the event ends, whichever way it sold.
                .releasableAt(event.getEndDate() != null
                        ? event.getEndDate().plusHours(payoutHoldHours) : null)
                .build();

        if (userId != null) {
            order.setUser(userRepository.getReferenceById(userId));
        }

        order = orderRepository.save(order);

        if (!ticketType.isFree() && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            String paystackRef = "PTK-" + UUID.randomUUID().toString().substring(0, 20).toUpperCase();
            order.setPaystackReference(paystackRef);
            String paystackUrl = paystackService.initializeTransaction(
                    request.getBuyerEmail(), totalAmount, paystackRef,
                    paystackConfig.getPaymentCallbackUrl()
            );
            order = orderRepository.save(order);

            return toResponse(order, null, paystackUrl);
        } else {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            // Hold converted to a sale — must not be swept later.
            order.setHoldExpiresAt(null);
            order = orderRepository.save(order);
            processPostPayment(order);

            // Stock was taken by reserveQuantity above; incrementing here would
            // double-count it.
            List<Ticket> tickets = generateTickets(order, ticketType, request.getQuantity());

            sendTicketConfirmationEmail(order, tickets);

            return toResponse(order, tickets, null);
        }
    }

    /**
     * Called from the success page after Paystack redirects back. Confirms the
     * charge with Paystack before issuing anything, because this endpoint is
     * public and the reference alone proves nothing.
     */
    @Transactional
    public OrderResponse verifyPayment(String paystackReference) {
        Order existing = orderRepository.findByPaystackReference(paystackReference)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (existing.getPaymentStatus() == PaymentStatus.PAID) {
            return toResponse(existing, ticketRepository.findByOrderId(existing.getId()), null);
        }

        if (!paystackService.verifyTransaction(paystackReference)) {
            // Release the seats this order was holding before failing it, or an
            // abandoned card attempt keeps them off sale until the sweeper runs.
            if (existing.getTicketTypeId() != null && existing.getQuantity() != null
                    && existing.getHoldExpiresAt() != null) {
                ticketTypeRepository.releaseQuantity(existing.getTicketTypeId(), existing.getQuantity());
            }
            existing.setPaymentStatus(PaymentStatus.FAILED);
            existing.setHoldExpiresAt(null);
            orderRepository.save(existing);
            throw new BadRequestException("Payment verification failed");
        }

        return finalizePaidOrder(paystackReference);
    }

    @Transactional
    public OrderResponse handlePaystackWebhook(String event, String paystackRef) {
        if (!"charge.success".equals(event)) {
            return null;
        }
        return finalizePaidOrder(paystackRef);
    }

    /**
     * Moves an order to PAID and issues its tickets, exactly once.
     *
     * <p>Both confirmation paths funnel through here — the success-page verify and
     * the Paystack webhook — and they can race each other, since a buyer often
     * returns from checkout at the same moment the webhook lands. Paystack also
     * retries webhooks. The transition is therefore a conditional UPDATE and only
     * the caller whose update affected a row mints tickets; everyone else reads
     * back what was already issued.
     */
    private OrderResponse finalizePaidOrder(String paystackReference) {
        // Claim before loading: claimPaid is a bulk update that clears the
        // persistence context, so anything read beforehand would be detached with
        // a stale status.
        int claimed = orderRepository.claimPaid(paystackReference, LocalDateTime.now());

        Order order = orderRepository.findByPaystackReference(paystackReference)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        TicketType ticketType = null;
        if (order.getTicketTypeId() != null) {
            ticketType = ticketTypeRepository.findById(order.getTicketTypeId()).orElse(null);
        }
        if (ticketType == null) {
            ticketType = ticketTypeRepository.findByEventId(order.getEvent().getId())
                    .stream().findFirst().orElse(null);
        }

        if (claimed == 0) {
            // Someone else already finalised it — return their tickets, don't mint more.
            return toResponse(order, ticketRepository.findByOrderId(order.getId()), null);
        }

        order.setHoldExpiresAt(null);
        order = orderRepository.save(order);
        processPostPayment(order);

        int quantity = order.getQuantity() != null ? order.getQuantity() : 1;

        // Stock was reserved when the order was created — on both the web and
        // external-channel paths — so it is deliberately not incremented here.
        List<Ticket> tickets = generateTickets(order, ticketType, quantity);

        sendTicketConfirmationEmail(order, tickets);

        // An order that arrived from a message channel must be delivered there, not
        // just emailed — its email is a synthesised placeholder nobody reads.
        //
        // This path can legitimately confirm such an order: Paystack redirects the
        // buyer to the success page, which calls /orders/verify, and that often wins
        // the race against the webhook. Delegating to the integration service keeps
        // one delivery implementation rather than two.
        if (order.getBuyerPhone() != null && !order.getBuyerPhone().isBlank()) {
            integrationOrderService.deliverExistingOrder(order.getPaystackReference());
        }

        return toResponse(order, tickets, null);
    }

    public List<OrderResponse> getUserOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(order -> toResponse(order, ticketRepository.findByOrderId(order.getId()), null))
                .toList();
    }

    public List<TicketResponse> getUserTickets(String email) {
        return ticketRepository.findByBuyerEmail(email)
                .stream()
                .map(this::toTicketResponse)
                .toList();
    }

    public OrderResponse getOrderByRef(String orderRef) {
        Order order = orderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return toResponse(order, ticketRepository.findByOrderId(order.getId()), null);
    }

    private void processPostPayment(Order order) {
        if (order.getDiscountCode() != null) {
            discountService.incrementUsage(order.getDiscountCode());
        }
        if (order.getReferralCode() != null) {
            referralService.trackSale(order.getReferralCode());
        }
    }

    private List<Ticket> generateTickets(Order order, TicketType ticketType, int quantity) {
        List<Ticket> tickets = new ArrayList<>();

        for (int i = 0; i < quantity; i++) {
            String qrText = qrSigningService.mint(order.getEvent().getId());

            Ticket ticket = Ticket.builder()
                    .ticketType(ticketType)
                    .order(order)
                    .event(order.getEvent())
                    .buyerEmail(order.getBuyerEmail())
                    .buyerName(order.getBuyerName())
                    .qrCode(qrText)
                    .status(TicketStatus.ACTIVE)
                    .build();

            tickets.add(ticketRepository.save(ticket));
        }

        return tickets;
    }

    private BigDecimal calculateFee(EventType eventType, BigDecimal subtotal, int quantity) {
        return pricingService.calculateFee(eventType, subtotal, quantity);
    }

    private OrderResponse toResponse(Order order, List<Ticket> tickets, String paystackUrl) {
        List<TicketResponse> ticketResponses = null;
        if (tickets != null) {
            ticketResponses = tickets.stream().map(this::toTicketResponse).toList();
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderRef(order.getOrderRef())
                .eventTitle(order.getEvent().getTitle())
                .eventSlug(order.getEvent().getSlug())
                .subtotal(order.getSubtotal())
                .feeAmount(order.getFeeAmount())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .currency(order.getCurrency())
                .buyerEmail(order.getBuyerEmail())
                .buyerName(order.getBuyerName())
                .paystackUrl(paystackUrl)
                .discountCode(order.getDiscountCode())
                .discountAmount(order.getDiscountAmount())
                .tickets(ticketResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketTypeName(ticket.getTicketType().getName())
                .eventTitle(ticket.getEvent().getTitle())
                .eventSlug(ticket.getEvent().getSlug())
                .buyerEmail(ticket.getBuyerEmail())
                .buyerName(ticket.getBuyerName())
                .qrCode(ticket.getQrCode())
                .status(ticket.getStatus())
                .purchasedAt(ticket.getPurchasedAt())
                .checkedInAt(ticket.getCheckedInAt())
                .build();
    }

    private void sendTicketConfirmationEmail(Order order, List<Ticket> tickets) {
        if (order.getBuyerEmail() == null || order.getBuyerEmail().isBlank()) return;

        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(order.getBuyerName()).append(",\n\n");
        body.append("Your ticket purchase is confirmed!\n\n");
        body.append("Event: ").append(order.getEvent().getTitle()).append("\n");
        body.append("Order Reference: ").append(order.getOrderRef()).append("\n");
        body.append("Amount Paid: NGN ").append(order.getTotalAmount()).append("\n\n");
        body.append("Your Ticket(s):\n");

        for (Ticket ticket : tickets) {
            body.append("- ").append(ticket.getTicketType().getName())
                .append(" | QR Code: ").append(ticket.getQrCode()).append("\n");
        }

        body.append("\nPresent your QR code at the event entrance for check-in.\n\n");
        body.append("Thank you for using Eventify!");

        // EmailService swallows and logs its own failures, so a mail problem cannot
        // unwind a completed sale. The previous empty catch here hid the outcome
        // entirely — a buyer's ticket could silently never arrive with nothing in
        // the logs to show for it.
        boolean sent = emailService.sendSimpleEmail(
                order.getBuyerEmail(),
                "Your Eventify Tickets - " + order.getEvent().getTitle(),
                body.toString()
        );

        if (!sent) {
            log.warn("Order {} is paid and its tickets are issued, but the confirmation " +
                     "email to {} was not sent. Tickets remain valid and retrievable.",
                    order.getOrderRef(), order.getBuyerEmail());
        }
    }
}
