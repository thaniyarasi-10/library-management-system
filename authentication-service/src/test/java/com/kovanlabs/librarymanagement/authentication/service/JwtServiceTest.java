package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    @Mock
    private Authentication authentication;

    private static final String BASE64_SECRET = "dGhpcy1pcy1hLXZlcnktc2VjdXJlLWJhc2U2NC1zZWNyZXQta2V5LTEyMzQ1Njc4OTA=";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secret", BASE64_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 3600000L); // 1 hour
        jwtService.init();
    }

    @Test
    void generateTokenFromAuthentication_shouldReturnValidToken() {
        when(authentication.getName()).thenReturn("testuser@example.com");
        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);

        String token = jwtService.generateToken(authentication);

        assertNotNull(token);
        assertEquals("testuser@example.com", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, "testuser@example.com"));
        assertFalse(jwtService.isTokenValid(token, "otheruser@example.com"));
    }

    @Test
    void generateTokenFromUser_shouldReturnValidToken() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setRole(RoleEnum.USER);

        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertEquals("user@example.com", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, "user@example.com"));
    }

    @Test
    void extractClaim_shouldExtractCustomClaim() {
        User user = new User();
        user.setEmail("admin@example.com");
        user.setRole(RoleEnum.ADMIN);

        String token = jwtService.generateToken(user);

        Date claimsExpiration = jwtService.extractClaim(token, Claims::getExpiration);
        assertNotNull(claimsExpiration);
        assertTrue(claimsExpiration.after(new Date()));
    }
}
