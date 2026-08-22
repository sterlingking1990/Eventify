package com.example.PTicketing.service;

import com.example.PTicketing.dto.request.PlatformSettingsRequest;
import com.example.PTicketing.dto.response.FeeInfoResponse;
import com.example.PTicketing.dto.response.PlatformSettingsResponse;
import com.example.PTicketing.entity.PlatformSettings;
import com.example.PTicketing.entity.User;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.repository.PlatformSettingsRepository;
import com.example.PTicketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Platform-wide settings, stored as a single row (id=1).
 *
 * <p>No seeder and no migration define the default — the row is created on first
 * read/write instead, so a fresh environment works without an extra setup step.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private static final Long SETTINGS_ID = 1L;
    private static final BigDecimal DEFAULT_FEE_PERCENT = BigDecimal.valueOf(5.00);
    private static final int DEFAULT_SLA_HOURS = 24;

    private final PlatformSettingsRepository platformSettingsRepository;
    private final UserRepository userRepository;

    @Transactional
    public PlatformSettings getOrCreate() {
        return platformSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(() -> platformSettingsRepository.save(
                        PlatformSettings.builder()
                                .id(SETTINGS_ID)
                                .cashoutFeePercent(DEFAULT_FEE_PERCENT)
                                .payoutSlaHours(DEFAULT_SLA_HOURS)
                                .updatedAt(LocalDateTime.now())
                                .build()));
    }

    public PlatformSettingsResponse getSettings() {
        return toResponse(getOrCreate());
    }

    public FeeInfoResponse getFeeInfo() {
        PlatformSettings settings = getOrCreate();
        return FeeInfoResponse.builder()
                .cashoutFeePercent(settings.getCashoutFeePercent())
                .payoutSlaHours(settings.getPayoutSlaHours())
                .build();
    }

    @Transactional
    public PlatformSettingsResponse updateSettings(PlatformSettingsRequest request, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        PlatformSettings settings = getOrCreate();
        settings.setCashoutFeePercent(request.getCashoutFeePercent());
        settings.setPayoutSlaHours(request.getPayoutSlaHours());
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(admin);

        return toResponse(platformSettingsRepository.save(settings));
    }

    private PlatformSettingsResponse toResponse(PlatformSettings settings) {
        return PlatformSettingsResponse.builder()
                .cashoutFeePercent(settings.getCashoutFeePercent())
                .payoutSlaHours(settings.getPayoutSlaHours())
                .updatedAt(settings.getUpdatedAt())
                .updatedByName(settings.getUpdatedBy() != null ? settings.getUpdatedBy().getFullName() : null)
                .build();
    }
}
