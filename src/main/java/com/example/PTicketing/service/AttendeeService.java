package com.example.PTicketing.service;

import com.example.PTicketing.dto.response.AttendeeResponse;
import com.example.PTicketing.entity.Event;
import com.example.PTicketing.entity.Order;
import com.example.PTicketing.entity.Ticket;
import com.example.PTicketing.enums.TicketStatus;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.exception.UnauthorizedException;
import com.example.PTicketing.repository.EventRepository;
import com.example.PTicketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * The organiser's guest list.
 *
 * <p>Fills a real operational gap: before this, an organiser could see that 300
 * tickets had sold but not who held them — no door list, no way to answer "I paid
 * but lost my ticket", no way to contact buyers if the venue changed.
 *
 * <p>Every method takes the requesting user id and verifies event ownership. That
 * is not belt-and-braces: the payload carries buyer names, emails and phone
 * numbers, so a role check alone would let any organiser read another's customer
 * list by changing the id in the URL.
 */
@Service
@RequiredArgsConstructor
public class AttendeeService {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public List<AttendeeResponse> getAttendees(Long eventId, Long userId, String search) {
        requireOwnership(eventId, userId);

        // Cancelled tickets are excluded — they do not admit anyone and would pad a
        // door list with people who should be turned away.
        List<Ticket> tickets = ticketRepository.findByEventId(eventId).stream()
                .filter(t -> t.getStatus() == TicketStatus.ACTIVE || t.getStatus() == TicketStatus.USED)
                .toList();

        String needle = search == null ? "" : search.trim().toLowerCase();

        return tickets.stream()
                .map(this::toResponse)
                .filter(a -> needle.isEmpty()
                        || contains(a.getBuyerName(), needle)
                        || contains(a.getBuyerEmail(), needle)
                        || contains(a.getBuyerPhone(), needle)
                        || contains(a.getOrderRef(), needle)
                        || contains(a.getQrCode(), needle))
                // Newest first: an organiser opening this mid-sale wants to see what
                // just happened, and at the gate they search rather than scroll.
                .sorted(Comparator.comparing(
                        AttendeeResponse::getPurchasedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * The same list as CSV, for printing or handing to door staff.
     *
     * <p>Deliberately omits the QR code. A door list is printed and passed around,
     * and every code on it admits someone — a mislaid printout should not be a set
     * of working tickets.
     */
    @Transactional(readOnly = true)
    public String exportCsv(Long eventId, Long userId) {
        List<AttendeeResponse> rows = getAttendees(eventId, userId, null);

        StringBuilder csv = new StringBuilder();
        csv.append("Name,Email,Phone,Ticket Type,Order Ref,Source,Amount,Purchased At,Status,Checked In\n");

        for (AttendeeResponse a : rows) {
            csv.append(csvCell(a.getBuyerName())).append(',')
               .append(csvCell(a.getBuyerEmail())).append(',')
               .append(csvCell(a.getBuyerPhone())).append(',')
               .append(csvCell(a.getTicketTypeName())).append(',')
               .append(csvCell(a.getOrderRef())).append(',')
               .append(csvCell(a.getSourceChannel())).append(',')
               .append(a.getAmountPaid() != null ? a.getAmountPaid().toPlainString() : "").append(',')
               .append(a.getPurchasedAt() != null ? a.getPurchasedAt().toString() : "").append(',')
               .append(a.getStatus() != null ? a.getStatus().name() : "").append(',')
               .append(a.isCheckedIn() ? "YES" : "NO").append('\n');
        }

        return csv.toString();
    }

    // ─── internals ───────────────────────────────────────────────────────────

    private void requireOwnership(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (event.getOrganizer() == null || !event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to view attendees for this event");
        }
    }

    private boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle);
    }

    /**
     * Quotes a CSV cell, and neutralises spreadsheet formula injection.
     *
     * <p>Buyer names arrive from a public checkout form. A value starting =, +, -
     * or @ is executed as a formula when the file is opened in Excel or Sheets, so
     * a name like {@code =HYPERLINK(...)} would run against whoever opens the door
     * list. Prefixing an apostrophe makes it inert text.
     */
    private String csvCell(String value) {
        if (value == null || value.isEmpty()) return "";

        String v = value;
        if (v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@")) {
            v = "'" + v;
        }
        // Escape embedded quotes, then wrap — required for any cell containing a
        // comma, quote or newline.
        return '"' + v.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }

    private AttendeeResponse toResponse(Ticket ticket) {
        Order order = ticket.getOrder();

        // What this attendee's ticket actually cost. An order covering four tickets
        // holds one total, so showing that total on each row would quadruple the
        // apparent revenue of a group purchase.
        BigDecimal amountPaid = null;
        if (order != null && order.getTotalAmount() != null) {
            int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
            amountPaid = order.getTotalAmount()
                    .divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
        }

        return AttendeeResponse.builder()
                .ticketId(ticket.getId())
                .ticketTypeName(ticket.getTicketType() != null ? ticket.getTicketType().getName() : null)
                .buyerName(ticket.getBuyerName())
                .buyerEmail(ticket.getBuyerEmail())
                .buyerPhone(order != null ? order.getBuyerPhone() : null)
                .orderRef(order != null ? order.getOrderRef() : null)
                .sourceChannel(order != null ? order.getSourceChannel() : null)
                .amountPaid(amountPaid)
                .purchasedAt(ticket.getPurchasedAt())
                .status(ticket.getStatus())
                // Read from the ticket rather than the check_ins table: the scanner
                // stamps this on check-in, so a per-ticket lookup would be an N+1
                // query for information already held here.
                .checkedIn(ticket.getCheckedInAt() != null)
                .checkedInAt(ticket.getCheckedInAt())
                .qrCode(ticket.getQrCode())
                .build();
    }
}
