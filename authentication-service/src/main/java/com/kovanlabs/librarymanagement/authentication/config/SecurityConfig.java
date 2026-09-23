package com.kovanlabs.librarymanagement.authentication.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${auth0.domain:dev-default.us.auth0.com}")
    private String auth0Domain;

    @Value("${auth0.audience:https://library-api.kovanlabs.com}")
    private String auth0Audience;

    @Value("${auth0.roles-claim:https://library.kovanlabs.com/roles}")
    private String rolesClaim;

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
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @ConditionalOnMissingBean(Auth0AuthoritiesConverter.class)
    public Auth0AuthoritiesConverter auth0AuthoritiesConverter() {
        return new Auth0AuthoritiesConverter(rolesClaim);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(auth0AuthoritiesConverter());
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        String issuerUri = normalizeIssuer(auth0Domain);
        String jwkSetUri = issuerUri + ".well-known/jwks.json";
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(auth0Audience);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);
        return jwtDecoder;
    }

    private String normalizeIssuer(String domain) {
        if (domain == null || domain.isBlank()) {
            return "https://auth0.local/";
        }
        String normalized = domain.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Public Auth Endpoints
                        .requestMatchers("/api/auth/**").permitAll()

                        // Static and Public Endpoints
                        .requestMatchers("/", "/index.html", "/*.css", "/*.js", "/*.html", "/favicon.ico", "/static/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()

                        // Books Public Endpoints
                        .requestMatchers(HttpMethod.GET, "/books", "/books/**")
                        .permitAll()

                        // Books Write / Delete (Permission or Admin Role)
                        .requestMatchers(HttpMethod.POST, "/books", "/books/**")
                        .hasAnyAuthority("books:write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/books", "/books/**")
                        .hasAnyAuthority("books:write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/books", "/books/**")
                        .hasAnyAuthority("books:delete", "ROLE_ADMIN")

                        // User Me Endpoint (Authenticated User/Admin)
                        .requestMatchers(HttpMethod.GET, "/user/me")
                        .authenticated()

                        // User Registration (Public)
                        .requestMatchers(HttpMethod.POST, "/user")
                        .permitAll()

                        // User Admin Endpoints (Permission or Admin Role)
                        .requestMatchers(HttpMethod.GET, "/user", "/user/**")
                        .hasAnyAuthority("users:read", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/user", "/user/**")
                        .hasAnyAuthority("users:write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/user", "/user/**")
                        .hasAnyAuthority("users:write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/user", "/user/**")
                        .hasAnyAuthority("users:delete", "ROLE_ADMIN")

                        // Borrow Endpoints (Permission or Role)
                        .requestMatchers("/borrow", "/borrow/**")
                        .hasAnyAuthority("borrow:read", "borrow:create", "borrow:update", "borrow:write", "ROLE_USER",
                                "ROLE_ADMIN")

                        // Membership Endpoints (Permission or Role)
                        .requestMatchers("/memberships", "/memberships/**")
                        .hasAnyAuthority("memberships:read", "memberships:write", "ROLE_USER", "ROLE_ADMIN")

                        // Fine Endpoints (Permission or Role)
                        .requestMatchers("/fines", "/fines/**")
                        .hasAnyAuthority("fines:read", "fines:write", "fines:pay", "ROLE_USER", "ROLE_ADMIN")

                        // All other endpoints require authentication
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }
}
