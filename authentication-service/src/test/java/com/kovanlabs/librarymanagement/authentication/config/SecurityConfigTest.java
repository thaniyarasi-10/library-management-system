package com.kovanlabs.librarymanagement.authentication.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityConfigTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private static JwtDecoder mockJwtDecoder;

    @RestController
    static class TestSecurityController {
        @GetMapping("/books")
        public ResponseEntity<String> getBooks() {
            return ResponseEntity.ok("books");
        }

        @PostMapping("/books")
        public ResponseEntity<String> createBook() {
            return ResponseEntity.ok("created");
        }

        @DeleteMapping("/books/{id}")
        public ResponseEntity<String> deleteBook(@PathVariable String id) {
            return ResponseEntity.ok("deleted");
        }

        @GetMapping("/user/me")
        public ResponseEntity<String> getMe() {
            return ResponseEntity.ok("me");
        }

        @GetMapping("/user")
        public ResponseEntity<String> getUsers() {
            return ResponseEntity.ok("users");
        }
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestSecurityConfig {
        @Bean
        public TestSecurityController testSecurityController() {
            return new TestSecurityController();
        }

        @Bean
        public JwtDecoder jwtDecoder() {
            return mockJwtDecoder;
        }
    }

    @BeforeEach
    void setUp() {
        mockJwtDecoder = mock(JwtDecoder.class);
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestSecurityConfig.class);
        context.refresh();

        FilterChainProxy springSecurityFilterChain = context.getBean("springSecurityFilterChain",
                FilterChainProxy.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void publicEndpoint_whenNoToken_shouldBeAllowed() throws Exception {
        mockMvc.perform(get("/books"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_whenNoToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/books"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_whenInvalidToken_shouldReturn401() throws Exception {
        when(mockJwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Invalid token signature"));

        mockMvc.perform(post("/books")
                .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_whenExpiredToken_shouldReturn401() throws Exception {
        OAuth2Error error = new OAuth2Error("invalid_token", "Jwt is expired", null);
        when(mockJwtDecoder.decode(anyString())).thenThrow(new JwtValidationException("Expired token", List.of(error)));

        mockMvc.perform(post("/books")
                .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_whenWrongIssuer_shouldReturn401() throws Exception {
        OAuth2Error error = new OAuth2Error("invalid_token", "Invalid issuer in token", null);
        when(mockJwtDecoder.decode(anyString())).thenThrow(new JwtValidationException("Wrong issuer", List.of(error)));

        mockMvc.perform(post("/books")
                .header("Authorization", "Bearer wrong-issuer-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_whenWrongAudience_shouldReturn401() throws Exception {
        OAuth2Error error = new OAuth2Error("invalid_token", "The required audience is missing", null);
        when(mockJwtDecoder.decode(anyString()))
                .thenThrow(new JwtValidationException("Wrong audience", List.of(error)));

        mockMvc.perform(post("/books")
                .header("Authorization", "Bearer wrong-audience-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_whenValidTokenWithRequiredPermission_shouldBeAllowed() throws Exception {
        Jwt jwt = createJwtWithClaims(Map.of(
                "permissions", List.of("books:write")));
        when(mockJwtDecoder.decode("valid-token")).thenReturn(jwt);

        mockMvc.perform(post("/books")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_whenValidTokenWithRoleAdmin_shouldBeAllowed() throws Exception {
        Jwt jwt = createJwtWithClaims(Map.of(
                "https://library.kovanlabs.com/roles", List.of("admin")));
        when(mockJwtDecoder.decode("admin-token")).thenReturn(jwt);

        mockMvc.perform(post("/books")
                .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_whenValidTokenLacksRequiredPermission_shouldReturn403() throws Exception {
        Jwt jwt = createJwtWithClaims(Map.of(
                "permissions", List.of("books:read"),
                "https://library.kovanlabs.com/roles", List.of("user")));
        when(mockJwtDecoder.decode("user-token")).thenReturn(jwt);

        mockMvc.perform(post("/books")
                .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void userMeEndpoint_whenValidToken_shouldBeAllowed() throws Exception {
        Jwt jwt = createJwtWithClaims(Map.of(
                "sub", "auth0|12345"));
        when(mockJwtDecoder.decode("me-token")).thenReturn(jwt);

        mockMvc.perform(get("/user/me")
                .header("Authorization", "Bearer me-token"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBookEndpoint_whenMissingDeletePermission_shouldReturn403() throws Exception {
        Jwt jwt = createJwtWithClaims(Map.of(
                "permissions", List.of("books:write")));
        when(mockJwtDecoder.decode("token-no-delete")).thenReturn(jwt);

        mockMvc.perform(delete("/books/1")
                .header("Authorization", "Bearer token-no-delete"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBookEndpoint_whenHasDeletePermission_shouldBeAllowed() throws Exception {
        Jwt jwt = createJwtWithClaims(Map.of(
                "permissions", List.of("books:delete")));
        when(mockJwtDecoder.decode("token-delete")).thenReturn(jwt);

        mockMvc.perform(delete("/books/1")
                .header("Authorization", "Bearer token-delete"))
                .andExpect(status().isOk());
    }

    private Jwt createJwtWithClaims(Map<String, Object> claims) {
        return Jwt.withTokenValue("mock-token-value")
                .header("alg", "RS256")
                .claim("sub", claims.getOrDefault("sub", "auth0|mock-user"))
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
