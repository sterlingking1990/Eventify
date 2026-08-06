package com.example.PTicketing.service;

import com.example.PTicketing.dto.request.*;
import com.example.PTicketing.dto.response.AuthResponse;
import com.example.PTicketing.dto.response.UserResponse;
import com.example.PTicketing.entity.PasswordResetToken;
import com.example.PTicketing.entity.User;
import com.example.PTicketing.enums.UserRole;
import com.example.PTicketing.exception.BadRequestException;
import com.example.PTicketing.exception.DuplicateResourceException;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.exception.UnauthorizedException;
import com.example.PTicketing.repository.PasswordResetTokenRepository;
import com.example.PTicketing.repository.UserRepository;
import com.example.PTicketing.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    public AuthResponse register(SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }

        UserRole role = UserRole.ATTENDEE;
        if (request.getRole() != null) {
            try {
                UserRole requested = UserRole.valueOf(request.getRole().toUpperCase());
                if (requested == UserRole.ATTENDEE || requested == UserRole.ORGANIZER) {
                    role = requested;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(role)
                .build();

        user = userRepository.save(user);
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    public AuthResponse googleAuth(GoogleAuthRequest request) {
        User user = userRepository.findByGoogleId(request.getGoogleId()).orElse(null);

        if (user == null) {
            user = userRepository.findByEmail(request.getEmail()).orElse(null);
        }

        if (user == null) {
            user = User.builder()
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .fullName(request.getFullName())
                    .googleId(request.getGoogleId())
                    .avatarUrl(request.getAvatarUrl())
                    .role(UserRole.ATTENDEE)
                    .build();
        } else {
            user.setGoogleId(request.getGoogleId());
            if (request.getAvatarUrl() != null) {
                user.setAvatarUrl(request.getAvatarUrl());
            }
        }

        user = userRepository.save(user);
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .build();

        tokenRepository.save(resetToken);

        String resetLink = baseUrl + "/reset-password?token=" + token;
        emailService.sendSimpleEmail(
                request.getEmail(),
                "Password Reset Request",
                "Click the link to reset your password: " + resetLink
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (resetToken.isUsed()) {
            throw new BadRequestException("Token already used");
        }

        if (resetToken.isExpired()) {
            throw new BadRequestException("Token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    public AuthResponse scannerLogin(ScannerLoginRequest request) {
        User user = userRepository.findByEmail(request.getScannerUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid scanner credentials"));

        if (user.getRole() != UserRole.SCANNER) {
            throw new UnauthorizedException("Account is not a scanner");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid scanner credentials");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toUserResponse(user);
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .phone(user.getPhone())
                .build();
    }
}
