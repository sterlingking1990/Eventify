package com.example.PTicketing.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates trusted server-to-server callers on {@code /api/v1/integration/**}.
 *
 * <p>These endpoints are called by another backend, not by a signed-in person, so
 * a user JWT is the wrong instrument. A shared key in {@code X-Service-Key} grants
 * the synthetic {@code ROLE_SERVICE} authority.
 *
 * <p>Deliberately does not fall back to allowing the request when no key is
 * configured — an unset {@code integration.service-key} leaves the endpoints
 * closed rather than open.
 */
@Slf4j
@Component
public class ServiceKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Service-Key";
    private static final String PATH_PREFIX = "/api/v1/integration/";

    @Value("${integration.service-key:}")
    private String serviceKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (serviceKey == null || serviceKey.isBlank()) {
            log.error("integration.service-key is not configured — rejecting call to {}",
                    request.getRequestURI());
            reject(response, "Integration endpoints are not configured");
            return;
        }

        String presented = request.getHeader(HEADER);

        if (presented == null || !constantTimeEquals(presented, serviceKey)) {
            log.warn("Rejected integration call to {} — {} header missing or invalid",
                    request.getRequestURI(), HEADER);
            reject(response, "Invalid service key");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "service", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    /** Avoids leaking key content through response timing. */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }
}
