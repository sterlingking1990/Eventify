package com.example.PTicketing.repository;

import com.example.PTicketing.entity.Event;
import com.example.PTicketing.enums.EventStatus;
import com.example.PTicketing.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Event> findByOrganizerId(Long organizerId);
    List<Event> findByStatusAndType(EventStatus status, EventType type);
    List<Event> findByStatus(EventStatus status);
    List<Event> findByCategoryIdAndStatus(Long categoryId, EventStatus status);
    List<Event> findByTypeAndStatus(EventType type, EventStatus status);
}
