package com.example.PTicketing.service;

import com.example.PTicketing.dto.response.AnalyticsResponse;
import com.example.PTicketing.entity.*;
import com.example.PTicketing.enums.PaymentStatus;
import com.example.PTicketing.enums.TicketStatus;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final CheckInRepository checkInRepository;
    private final TicketTypeRepository ticketTypeRepository;

    public AnalyticsResponse getEventAnalytics(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        List<Order> paidOrders = orderRepository.findByEventId(eventId)
                .stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PAID)
                .toList();

        List<Ticket> allTickets = ticketRepository.findByEventId(eventId);
        List<Ticket> activeTickets = allTickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.ACTIVE || t.getStatus() == TicketStatus.USED)
                .toList();

        long totalTicketsSold = activeTickets.size();
        long totalCheckIns = checkInRepository.countByEventId(eventId);
        double checkInPercentage = totalTicketsSold > 0
                ? Math.round((totalCheckIns * 10000.0 / totalTicketsSold)) / 100.0
                : 0;

        BigDecimal totalRevenue = paidOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFees = paidOrders.stream()
                .map(o -> o.getFeeAmount() != null ? o.getFeeAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netRevenue = totalRevenue.subtract(totalFees);

        List<TicketType> ticketTypes = ticketTypeRepository.findByEventId(eventId);
        Map<String, Long> ticketsByType = ticketTypes.stream()
                .collect(Collectors.toMap(
                        TicketType::getName,
                        tt -> allTickets.stream()
                                .filter(t -> t.getTicketType().getId().equals(tt.getId()))
                                .count()
                ));

        Map<LocalDate, List<Ticket>> ticketsByDate = allTickets.stream()
                .filter(t -> t.getPurchasedAt() != null)
                .collect(Collectors.groupingBy(t -> t.getPurchasedAt().toLocalDate()));

        List<AnalyticsResponse.DailySales> dailySales = ticketsByDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<Ticket> dayTickets = entry.getValue();
                    BigDecimal dayRevenue = paidOrders.stream()
                            .filter(o -> o.getCreatedAt() != null
                                    && o.getCreatedAt().toLocalDate().equals(date))
                            .map(Order::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return AnalyticsResponse.DailySales.builder()
                            .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            .ticketsSold(dayTickets.size())
                            .revenue(dayRevenue)
                            .build();
                })
                .sorted(Comparator.comparing(AnalyticsResponse.DailySales::getDate))
                .toList();

        return AnalyticsResponse.builder()
                .totalTicketsSold(totalTicketsSold)
                .totalCheckIns(totalCheckIns)
                .checkInPercentage(checkInPercentage)
                .totalRevenue(totalRevenue)
                .totalFees(totalFees)
                .netRevenue(netRevenue)
                .ticketsByType(ticketsByType)
                .dailySales(dailySales)
                .build();
    }
}
