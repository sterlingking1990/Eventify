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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PaystackService paystackService;
    private final QrCodeService qrCodeService;
    private final EmailService emailService;
    private final DiscountService discountService;
    private final ReferralService referralService;
    private final PaystackConfig paystackConfig;

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

        int available = ticketType.getQuantity() - ticketType.getTicketsSold();
        if (available < request.getQuantity()) {
            throw new BadRequestException("Not enough tickets available. Only " + available + " left");
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
                .build();

        if (userId != null) {
            order.setUser(userRepository.getReferenceById(userId));
        }

        order = orderRepository.save(order);

        if (method == PaymentMethod.TRANSFER) {
            order.setPaymentStatus(PaymentStatus.PENDING_VERIFICATION);
            order = orderRepository.save(order);
            return toResponse(order, null, null);
        }

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
            order = orderRepository.save(order);
            processPostPayment(order);

            List<Ticket> tickets = generateTickets(order, ticketType, request.getQuantity());
            ticketType.setTicketsSold(ticketType.getTicketsSold() + request.getQuantity());
            ticketTypeRepository.save(ticketType);

            sendTicketConfirmationEmail(order, tickets);

            return toResponse(order, tickets, null);
        }
    }

    @Transactional
    public OrderResponse verifyPayment(String paystackReference) {
        Order order = orderRepository.findByPaystackReference(paystackReference)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            List<Ticket> tickets = ticketRepository.findByOrderId(order.getId());
            return toResponse(order, tickets, null);
        }

        boolean verified = paystackService.verifyTransaction(paystackReference);

        if (!verified) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            orderRepository.save(order);
            throw new BadRequestException("Payment verification failed");
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order = orderRepository.save(order);
        processPostPayment(order);

        TicketType ticketType = null;
        if (order.getTicketTypeId() != null) {
            ticketType = ticketTypeRepository.findById(order.getTicketTypeId()).orElse(null);
        }
        if (ticketType == null) {
            ticketType = ticketTypeRepository.findByEventId(order.getEvent().getId())
                    .stream().findFirst().orElse(null);
        }
        int quantity = order.getQuantity() != null ? order.getQuantity() : 1;

        List<Ticket> tickets = generateTickets(order, ticketType, quantity);
        if (ticketType != null) {
            ticketType.setTicketsSold(ticketType.getTicketsSold() + quantity);
            ticketTypeRepository.save(ticketType);
        }

        sendTicketConfirmationEmail(order, tickets);

        return toResponse(order, tickets, null);
    }

    @Transactional
    public OrderResponse handlePaystackWebhook(String event, String paystackRef) {
        if (!"charge.success".equals(event)) {
            return null;
        }

        Order order = orderRepository.findByPaystackReference(paystackRef)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            List<Ticket> tickets = ticketRepository.findByOrderId(order.getId());
            return toResponse(order, tickets, null);
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order = orderRepository.save(order);
        processPostPayment(order);

        TicketType ticketType = null;
        if (order.getTicketTypeId() != null) {
            ticketType = ticketTypeRepository.findById(order.getTicketTypeId()).orElse(null);
        }
        if (ticketType == null) {
            ticketType = ticketTypeRepository.findByEventId(order.getEvent().getId())
                    .stream().findFirst().orElse(null);
        }
        int quantity = order.getQuantity() != null ? order.getQuantity() : 1;

        List<Ticket> tickets = generateTickets(order, ticketType, quantity);
        if (ticketType != null) {
            ticketType.setTicketsSold(ticketType.getTicketsSold() + quantity);
            ticketTypeRepository.save(ticketType);
        }

        sendTicketConfirmationEmail(order, tickets);

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
            String qrText = qrCodeService.generateQrCodeText();

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
        if (subtotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return switch (eventType) {
            case VOTING -> subtotal.multiply(BigDecimal.valueOf(0.08))
                    .setScale(2, RoundingMode.HALF_UP);
            case NORMAL -> subtotal.multiply(BigDecimal.valueOf(0.05))
                    .add(BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(quantity)))
                    .setScale(2, RoundingMode.HALF_UP);
        };
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

        try {
            emailService.sendSimpleEmail(
                order.getBuyerEmail(),
                "Your Eventify Tickets - " + order.getEvent().getTitle(),
                body.toString()
            );
        } catch (Exception e) {
            // Email failure should not break the order flow
        }
    }
}
