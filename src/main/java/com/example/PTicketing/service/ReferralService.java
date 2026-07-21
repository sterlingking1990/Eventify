package com.example.PTicketing.service;

import com.example.PTicketing.dto.response.ReferralLinkResponse;
import com.example.PTicketing.entity.Event;
import com.example.PTicketing.entity.ReferralLink;
import com.example.PTicketing.entity.User;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.repository.EventRepository;
import com.example.PTicketing.repository.ReferralLinkRepository;
import com.example.PTicketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralLinkRepository referralRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReferralLinkResponse generateReferral(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ReferralLink existing = referralRepository.findByUserIdAndEventId(userId, eventId).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        String code = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ReferralLink rl = ReferralLink.builder()
                .user(user)
                .event(event)
                .code(code)
                .build();

        rl = referralRepository.save(rl);
        return toResponse(rl);
    }

    public List<ReferralLinkResponse> getMyReferrals(Long userId) {
        return referralRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ReferralLinkResponse getReferralStats(String code) {
        ReferralLink rl = referralRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Referral link not found"));
        return toResponse(rl);
    }

    @Transactional
    public void trackClick(String code) {
        ReferralLink rl = referralRepository.findByCode(code).orElse(null);
        if (rl != null) {
            rl.setClicks(rl.getClicks() + 1);
            referralRepository.save(rl);
        }
    }

    @Transactional
    public void trackSale(String code) {
        ReferralLink rl = referralRepository.findByCode(code).orElse(null);
        if (rl != null) {
            rl.setTicketsSold(rl.getTicketsSold() + 1);
            referralRepository.save(rl);
        }
    }

    private ReferralLinkResponse toResponse(ReferralLink rl) {
        return ReferralLinkResponse.builder()
                .id(rl.getId())
                .code(rl.getCode())
                .eventTitle(rl.getEvent().getTitle())
                .eventId(rl.getEvent().getId())
                .clicks(rl.getClicks())
                .ticketsSold(rl.getTicketsSold())
                .createdAt(rl.getCreatedAt())
                .build();
    }
}
