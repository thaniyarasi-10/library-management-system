package com.kovanlabs.librarymanagement.authentication.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Objects;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final Auth0Properties auth0Properties;

    @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:3000,http://localhost:5173,http://127.0.0.1:8080,http://127.0.0.1:5173,http://127.0.0.1:5500}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new Auth0AuthoritiesConverter(auth0Properties.getRolesClaim()));
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        String issuerUri = normalizeIssuer(auth0Properties.getDomain());
        String jwkSetUri = issuerUri + ".well-known/jwks.json";
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
            .withJwkSetUri(jwkSetUri)
            .build();

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(auth0Properties.getAudience());
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);
        return jwtDecoder;
    }

    private String normalizeIssuer(String domain) {
        if (Objects.isNull(domain) || domain.isBlank()) {
            return "https://auth0.local/";
        }
        String trimmed = domain.trim();
        String withProtocol = (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))
                ? "https://" + trimmed
                : trimmed;
        return withProtocol.endsWith("/") ? withProtocol : withProtocol + "/";
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Static and Public Endpoints
                        .requestMatchers("/", "/index.html", "/*.css", "/*.js", "/*.html", "/favicon.ico", "/static/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()

                        // Books Public Endpoints
                        .requestMatchers(HttpMethod.GET, "/api/books", "/api/books/**", "/books", "/books/**")
                        .permitAll()

                        // Books Write / Delete (Permission or Admin Role)
                        .requestMatchers(HttpMethod.POST, "/api/books", "/api/books/**", "/books", "/books/**")
                        .hasAnyAuthority("books:write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/books", "/api/books/**", "/books", "/books/**")
                        .hasAnyAuthority("books:write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/books", "/api/books/**", "/books", "/books/**")
                        .hasAnyAuthority("books:delete", "ROLE_ADMIN")

                        // User Me & Login Completion Endpoints (Authenticated User/Admin)
                        .requestMatchers(HttpMethod.GET, "/api/user/me", "/user/me")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/user/callback", "/api/user/login-success", "/api/user/login",
                                "/user/callback", "/user/login-success", "/user/login")
                        .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/user/me", "/user/me")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/user/me/request-reactivation", "/user/me/request-reactivation")
                        .authenticated()

                        // User Payment Profile / Bank Account Details (Authenticated User/Admin)
                        .requestMatchers("/api/user/payment-profile", "/user/payment-profile",
                                "/api/user/payment-profile/**", "/user/payment-profile/**")
                        .authenticated()

                        // User Registration (Public)
                        .requestMatchers(HttpMethod.POST, "/api/user", "/user")
                        .permitAll()

                        // User Reactivation and Deactivation (Admin)
                        .requestMatchers(HttpMethod.POST,
                                "/api/user/*/deactivate", "/api/user/*/approve-reactivation", "/api/user/*/reject-reactivation",
                                "/user/*/deactivate", "/user/*/approve-reactivation", "/user/*/reject-reactivation")
                        .hasAnyAuthority("users:write", "ROLE_ADMIN")

                        // User Admin / Profile Update Endpoints (Permission or Role)
                        .requestMatchers(HttpMethod.GET, "/api/user", "/api/user/**", "/user", "/user/**")
                        .hasAnyAuthority("users:read", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/user", "/api/user/**", "/user", "/user/**")
                        .hasAnyAuthority("users:write", "ROLE_ADMIN", "ROLE_USER")
                        .requestMatchers(HttpMethod.PATCH, "/api/user", "/api/user/**", "/user", "/user/**")
                        .hasAnyAuthority("users:write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/user", "/api/user/**", "/user", "/user/**")
                        .hasAnyAuthority("users:delete", "ROLE_ADMIN")

                        // Borrow Endpoints (Permission, Role, or Authenticated User)
                        .requestMatchers(HttpMethod.GET, "/api/borrow/me", "/borrow/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/borrow", "/borrow").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/borrow/*", "/borrow/*").authenticated()
                        .requestMatchers("/api/borrow", "/api/borrow/**", "/borrow", "/borrow/**")
                        .hasAnyAuthority("borrow:read", "borrow:create", "borrow:update", "borrow:write", "ROLE_USER", "ROLE_ADMIN")

                        // Membership Endpoints (Permission, Role, or Authenticated User)
                        .requestMatchers(HttpMethod.GET, "/api/memberships/me", "/memberships/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/memberships/apply", "/memberships/apply").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/memberships/sign", "/memberships/sign").authenticated()
                        .requestMatchers("/api/memberships", "/api/memberships/**", "/memberships", "/memberships/**")
                        .hasAnyAuthority("memberships:read", "memberships:write", "ROLE_USER", "ROLE_ADMIN")

                        // Fine Endpoints (Permission, Role, or Authenticated User)
                        .requestMatchers(HttpMethod.GET, "/api/fines/me", "/fines/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/fines/*/pay", "/fines/*/pay").authenticated()
                        .requestMatchers("/api/fines", "/api/fines/**", "/fines", "/fines/**")
                        .hasAnyAuthority("fines:read", "fines:write", "fines:pay", "ROLE_USER", "ROLE_ADMIN")

                        // Donation Endpoints (Permission, Role, or Authenticated User)
                        .requestMatchers(HttpMethod.GET, "/api/donations/me", "/donations/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/donations", "/donations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/donations", "/donations").hasAnyAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/donations/*/approve", "/api/donations/*/reject",
                                "/donations/*/approve", "/donations/*/reject").hasAnyAuthority("donations:write", "ROLE_ADMIN")

                        // Payment & Razorpay Webhook Endpoints
                        .requestMatchers(HttpMethod.POST,
                                "/api/webhook/razorpay", "/webhook/razorpay",
                                "/api/payments/webhook", "/payments/webhook")
                        .permitAll()
                        .requestMatchers("/api/payments", "/api/payments/**", "/payments", "/payments/**").authenticated()

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }
}
