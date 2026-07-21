package com.example.PTicketing.repository;

import com.example.PTicketing.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderRef(String orderRef);
    Optional<Order> findByPaystackReference(String paystackReference);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findByEventId(Long eventId);
}
