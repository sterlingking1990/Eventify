package com.example.PTicketing.repository;

import com.example.PTicketing.entity.ReferralLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferralLinkRepository extends JpaRepository<ReferralLink, Long> {
    Optional<ReferralLink> findByCode(String code);
    List<ReferralLink> findByUserId(Long userId);
    List<ReferralLink> findByEventId(Long eventId);
    Optional<ReferralLink> findByUserIdAndEventId(Long userId, Long eventId);
}
