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
                        .requestMatchers(HttpMethod.PUT, "/user/me")
                        .authenticated()

                        // User Registration (Public)
                        .requestMatchers(HttpMethod.POST, "/user")
                        .permitAll()

                        // User Admin / Profile Update Endpoints (Permission or Role)
                        .requestMatchers(HttpMethod.GET, "/user", "/user/**")
                        .hasAnyAuthority("users:read", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/user", "/user/**")
                        .hasAnyAuthority("users:write", "ROLE_ADMIN", "ROLE_USER")
                        .requestMatchers(HttpMethod.PATCH, "/user", "/user/**")
                        .hasAnyAuthority("users:write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/user", "/user/**")
                        .hasAnyAuthority("users:delete", "ROLE_ADMIN")

                        // Borrow Endpoints (Permission, Role, or Authenticated User)
                        .requestMatchers(HttpMethod.GET, "/borrow/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/borrow").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/borrow/*").authenticated()
                        .requestMatchers("/borrow", "/borrow/**")
                        .hasAnyAuthority("borrow:read", "borrow:create", "borrow:update", "borrow:write", "ROLE_USER", "ROLE_ADMIN")

                        // Membership Endpoints (Permission, Role, or Authenticated User)
                        .requestMatchers(HttpMethod.GET, "/memberships/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/memberships/apply").authenticated()
                        .requestMatchers(HttpMethod.POST, "/memberships/sign").authenticated()
                        .requestMatchers("/memberships", "/memberships/**")
                        .hasAnyAuthority("memberships:read", "memberships:write", "ROLE_USER", "ROLE_ADMIN")

                        // Fine Endpoints (Permission, Role, or Authenticated User)
                        .requestMatchers(HttpMethod.GET, "/fines/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/fines/*/pay").authenticated()
                        .requestMatchers("/fines", "/fines/**")
                        .hasAnyAuthority("fines:read", "fines:write", "fines:pay", "ROLE_USER", "ROLE_ADMIN")

                        // All other endpoints require authentication
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
