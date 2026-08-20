package com.example.PTicketing.config;

import com.example.PTicketing.entity.User;
import com.example.PTicketing.enums.UserRole;
import com.example.PTicketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "emmanuelezekwu63@gmail.com";
    private static final String ADMIN_PASSWORD = "%%Emmanuel132";
    private static final String ADMIN_NAME = "Emmanuel Admin";

    @Override
    public void run(String... args) {
        User existing = userRepository.findByEmail(ADMIN_EMAIL).orElse(null);

        if (existing != null) {
            boolean changed = false;
            if (existing.getRole() != UserRole.ADMIN) {
                existing.setRole(UserRole.ADMIN);
                changed = true;
            }
            if (!passwordEncoder.matches(ADMIN_PASSWORD, existing.getPassword())) {
                existing.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
                changed = true;
            }
            if (changed) {
                userRepository.save(existing);
                log.info("Admin user updated: {} (role=ADMIN, password reset)", ADMIN_EMAIL);
            }
            return;
        }

        User admin = User.builder()
                .email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName(ADMIN_NAME)
                .role(UserRole.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("Admin user created: {}", ADMIN_EMAIL);
    }
}
