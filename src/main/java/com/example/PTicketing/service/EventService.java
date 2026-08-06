package com.example.PTicketing.service;

import com.example.PTicketing.dto.request.CreateEventRequest;
import com.example.PTicketing.dto.request.TicketTypeRequest;
import com.example.PTicketing.dto.request.UpdateEventRequest;
import com.example.PTicketing.dto.response.*;
import com.example.PTicketing.entity.Category;
import com.example.PTicketing.entity.Event;
import com.example.PTicketing.entity.TicketType;
import com.example.PTicketing.entity.User;
import com.example.PTicketing.enums.EventStatus;
import com.example.PTicketing.exception.BadRequestException;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.exception.UnauthorizedException;
import com.example.PTicketing.repository.CategoryRepository;
import com.example.PTicketing.repository.EventRepository;
import com.example.PTicketing.repository.TicketTypeRepository;
import com.example.PTicketing.repository.UserRepository;
import com.example.PTicketing.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserRepository userRepository;
    private final EventNotificationService eventNotificationService;

    public List<EventListResponse> getAllEvents(String category, String type, String search) {
        List<Event> events;
        LocalDateTime now = LocalDateTime.now();

        String cat = (category != null && !category.isBlank()) ? category : null;
        String typ = (type != null && !type.isBlank()) ? type : null;
        String q = (search != null && !search.isBlank()) ? search : null;

        if (cat != null && typ != null) {
            Category categoryObj = categoryRepository.findBySlug(cat).orElse(null);
            if (categoryObj == null) return List.of();
            com.example.PTicketing.enums.EventType eventType =
                    com.example.PTicketing.enums.EventType.valueOf(typ.toUpperCase());
            events = eventRepository.findByCategoryIdAndStatus(categoryObj.getId(), EventStatus.PUBLISHED)
                    .stream()
                    .filter(e -> e.getType() == eventType)
                    .filter(e -> e.getEndDate() == null || e.getEndDate().isAfter(now))
                    .toList();
        } else if (cat != null) {
            Category categoryObj = categoryRepository.findBySlug(cat).orElse(null);
            if (categoryObj == null) return List.of();
            events = eventRepository.findByCategoryIdAndStatus(categoryObj.getId(), EventStatus.PUBLISHED)
                    .stream()
                    .filter(e -> e.getEndDate() == null || e.getEndDate().isAfter(now))
                    .toList();
        } else if (typ != null) {
            com.example.PTicketing.enums.EventType eventType =
                    com.example.PTicketing.enums.EventType.valueOf(typ.toUpperCase());
            events = eventRepository.findByTypeAndStatus(eventType, EventStatus.PUBLISHED)
                    .stream()
                    .filter(e -> e.getEndDate() == null || e.getEndDate().isAfter(now))
                    .toList();
        } else {
            events = eventRepository.findByStatusAndEndDateAfterOrEndDateIsNull(EventStatus.PUBLISHED, now);
        }

        if (q != null) {
            String lower = q.toLowerCase();
            events = events.stream()
                    .filter(e -> e.getTitle().toLowerCase().contains(lower)
                            || (e.getDescription() != null && e.getDescription().toLowerCase().contains(lower))
                            || (e.getVenue() != null && e.getVenue().toLowerCase().contains(lower)))
                    .toList();
        }

        return events.stream()
                .map(this::toListResponse)
                .toList();
    }

    public EventResponse getEventBySlug(String slug) {
        Event event = eventRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        return toResponse(event, true);
    }

    public EventResponse getEventBySlugPublic(String slug) {
        Event event = eventRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        return toResponse(event, false);
    }

    public List<EventListResponse> getMyEvents(Long userId) {
        return eventRepository.findByOrganizerId(userId)
                .stream()
                .map(this::toListResponse)
                .toList();
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, Long organizerId) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizer not found"));

        String slug = SlugUtils.uniqueSlug(request.getTitle());
        while (eventRepository.existsBySlug(slug)) {
            slug = SlugUtils.uniqueSlug(request.getTitle());
        }

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .flyerImage(request.getFlyerImage())
                .organizer(organizer)
                .category(category)
                .type(request.getType())
                .status(EventStatus.PUBLISHED)
                .venue(request.getVenue())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .slug(slug)
                .bankName(request.getBankName())
                .bankAccountNumber(request.getBankAccountNumber())
                .bankAccountName(request.getBankAccountName())
                .build();

        event = eventRepository.save(event);

        if (request.getTicketTypes() != null) {
            for (TicketTypeRequest ttReq : request.getTicketTypes()) {
                TicketType tt = TicketType.builder()
                        .event(event)
                        .name(ttReq.getName())
                        .description(ttReq.getDescription())
                        .price(ttReq.getPrice())
                        .quantity(ttReq.getQuantity())
                        .isFree(ttReq.isFree())
                        .maxPerOrder(ttReq.getMaxPerOrder() != null ? ttReq.getMaxPerOrder() : 10)
                        .salesStartDate(ttReq.getSalesStartDate())
                        .salesEndDate(ttReq.getSalesEndDate())
                        .build();
                ticketTypeRepository.save(tt);
            }
        }

        eventNotificationService.notifyUsersAboutEvent(event, organizerId);

        return toResponse(event, true);
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to update this event");
        }

        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getFlyerImage() != null) {
            event.setFlyerImage(request.getFlyerImage());
        }
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId()).orElse(null);
            event.setCategory(category);
        }
        if (request.getType() != null) {
            event.setType(request.getType());
        }
        if (request.getStatus() != null) {
            event.setStatus(request.getStatus());
        }
        if (request.getVenue() != null) {
            event.setVenue(request.getVenue());
        }
        if (request.getLatitude() != null) {
            event.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            event.setLongitude(request.getLongitude());
        }
        if (request.getStartDate() != null) {
            event.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            event.setEndDate(request.getEndDate());
        }
        if (request.getBankName() != null) {
            event.setBankName(request.getBankName());
        }
        if (request.getBankAccountNumber() != null) {
            event.setBankAccountNumber(request.getBankAccountNumber());
        }
        if (request.getBankAccountName() != null) {
            event.setBankAccountName(request.getBankAccountName());
        }

        event = eventRepository.save(event);
        return toResponse(event, true);
    }

    @Transactional
    public void deleteEvent(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to delete this event");
        }

        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);
    }

    @Transactional
    public TicketTypeResponse createTicketType(Long eventId, TicketTypeRequest request, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to modify this event");
        }

        TicketType tt = TicketType.builder()
                .event(event)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .isFree(request.isFree())
                .maxPerOrder(request.getMaxPerOrder() != null ? request.getMaxPerOrder() : 10)
                .salesStartDate(request.getSalesStartDate())
                .salesEndDate(request.getSalesEndDate())
                .build();

        tt = ticketTypeRepository.save(tt);
        return toTicketTypeResponse(tt);
    }

    public List<TicketTypeResponse> getTicketTypes(Long eventId) {
        return ticketTypeRepository.findByEventId(eventId)
                .stream()
                .map(this::toTicketTypeResponse)
                .toList();
    }

    @Transactional
    public void deleteTicketType(Long eventId, Long ticketTypeId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to modify this event");
        }

        TicketType tt = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found"));

        if (!tt.getEvent().getId().equals(eventId)) {
            throw new BadRequestException("Ticket type does not belong to this event");
        }

        ticketTypeRepository.delete(tt);
    }

    private EventResponse toResponse(Event event, boolean includeBankDetails) {
        List<TicketTypeResponse> ticketTypes = ticketTypeRepository.findByEventId(event.getId())
                .stream()
                .map(this::toTicketTypeResponse)
                .toList();

        CategoryResponse categoryResponse = null;
        if (event.getCategory() != null) {
            categoryResponse = CategoryResponse.builder()
                    .id(event.getCategory().getId())
                    .name(event.getCategory().getName())
                    .slug(event.getCategory().getSlug())
                    .build();
        }

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .flyerImage(event.getFlyerImage())
                .category(categoryResponse)
                .type(event.getType())
                .status(event.getStatus())
                .venue(event.getVenue())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .slug(event.getSlug())
                .organizerName(event.getOrganizer().getFullName())
                .organizerId(event.getOrganizer().getId())
                .ticketTypes(ticketTypes)
                .bankName(includeBankDetails ? event.getBankName() : null)
                .bankAccountNumber(includeBankDetails ? event.getBankAccountNumber() : null)
                .bankAccountName(includeBankDetails ? event.getBankAccountName() : null)
                .createdAt(event.getCreatedAt())
                .build();
    }

    private EventListResponse toListResponse(Event event) {
        List<TicketType> types = ticketTypeRepository.findByEventId(event.getId());
        BigDecimal minPrice = types.stream()
                .filter(tt -> !tt.isFree())
                .min(Comparator.comparing(TicketType::getPrice))
                .map(TicketType::getPrice)
                .orElse(BigDecimal.ZERO);

        return EventListResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .flyerImage(event.getFlyerImage())
                .categoryName(event.getCategory() != null ? event.getCategory().getName() : null)
                .type(event.getType())
                .status(event.getStatus())
                .venue(event.getVenue())
                .startDate(event.getStartDate())
                .slug(event.getSlug())
                .organizerName(event.getOrganizer().getFullName())
                .minPrice(minPrice)
                .build();
    }

    private TicketTypeResponse toTicketTypeResponse(TicketType tt) {
        return TicketTypeResponse.builder()
                .id(tt.getId())
                .name(tt.getName())
                .description(tt.getDescription())
                .price(tt.getPrice())
                .quantity(tt.getQuantity())
                .ticketsSold(tt.getTicketsSold())
                .isFree(tt.isFree())
                .maxPerOrder(tt.getMaxPerOrder())
                .salesStartDate(tt.getSalesStartDate())
                .salesEndDate(tt.getSalesEndDate())
                .build();
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void completePastEvents() {
        List<Event> pastEvents = eventRepository.findByStatusAndEndDateBefore(
                EventStatus.PUBLISHED, LocalDateTime.now());
        for (Event event : pastEvents) {
            event.setStatus(EventStatus.COMPLETED);
            eventRepository.save(event);
        }
        if (!pastEvents.isEmpty()) {
            log.info("Auto-completed {} past events", pastEvents.size());
        }
    }
}
