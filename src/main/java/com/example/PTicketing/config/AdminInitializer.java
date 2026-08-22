package com.example.PTicketing.config;

import com.example.PTicketing.entity.User;
import com.example.PTicketing.enums.UserRole;
import com.example.PTicketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures a first admin account exists, sourced from {@code ADMIN_EMAIL} /
 * {@code ADMIN_PASSWORD} rather than baked into source: a hardcoded credential
 * here would sit in git history in plaintext, and forcibly resetting whichever
 * account happens to hold that email would let anyone who signs up with it get
 * promoted to admin and lock out the real admin.
 *
 * <p>Only ever creates — never promotes or resets an account that already
 * exists under this email, admin or not. Rotating the bootstrap admin's
 * password is a deliberate, manual action, not a side effect of a restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.email:}")
    private String adminEmail;

    @Value("${admin.bootstrap.password:}")
    private String adminPassword;

    @Value("${admin.bootstrap.name:Admin}")
    private String adminName;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL / ADMIN_PASSWORD not set — skipping admin bootstrap. " +
                    "No admin account will be created automatically.");
            return;
        }

        User existing = userRepository.findByEmail(adminEmail).orElse(null);
        if (existing != null) {
            if (existing.getRole() != UserRole.ADMIN) {
                log.warn("A user already exists with the bootstrap admin email {} but is not ADMIN — " +
                        "leaving it untouched. Promote it manually if that's intended.", adminEmail);
            } else {
                log.info("Admin user already exists: {}", adminEmail);
            }
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .fullName(adminName)
                .role(UserRole.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("Admin user created: {}", adminEmail);
    }
}
