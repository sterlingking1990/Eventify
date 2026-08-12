package com.example.PTicketing.config;

import com.example.PTicketing.security.JwtAuthenticationFilter;
import com.example.PTicketing.security.ServiceKeyAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ServiceKeyAuthFilter serviceKeyAuthFilter;
    private final UserDetailsService userDetailsService;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/categories/**",
                                "/api/v1/newsletter/**",
                                "/api/v1/paystack/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/initialize").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/verify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/my-events").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/analytics").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/complimentary").authenticated()
                        // MUST be listed before the permitAll on /api/v1/events/** below.
                        // These carry buyer names, emails, phone numbers and gate codes;
                        // matched by that catch-all they would be world-readable.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/attendees").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/attendees/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/events/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/events/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders").authenticated()
                        .requestMatchers("/api/v1/check-in/**").authenticated()
                        .requestMatchers("/api/v1/tickets/**").authenticated()
                        // Server-to-server. Authenticated by ServiceKeyAuthFilter, which
                        // populates ROLE_SERVICE; the controller then enforces that role.
                        .requestMatchers("/api/v1/integration/**").hasRole("SERVICE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/**").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\",\"data\":null}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Access denied\",\"data\":null}");
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(serviceKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(java.util.List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
