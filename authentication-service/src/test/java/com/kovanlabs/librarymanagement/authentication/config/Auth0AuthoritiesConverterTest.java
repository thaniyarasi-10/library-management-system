package com.kovanlabs.librarymanagement.authentication.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Auth0AuthoritiesConverterTest {

    private static final String NAMESPACED_ROLES = "https://library.kovanlabs.com/roles";
    private Auth0AuthoritiesConverter converter;

    @BeforeEach
    void setUp() {
        converter = new Auth0AuthoritiesConverter(NAMESPACED_ROLES);
    }

    @Test
    void convert_whenPermissionsClaimPresent_shouldMapToAuthorities() {
        Jwt jwt = createMockJwt(Map.of(
                "permissions", List.of("books:read", "books:write", "books:delete")
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        Set<String> authorityStrings = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertEquals(3, authorityStrings.size());
        assertTrue(authorityStrings.contains("books:read"));
        assertTrue(authorityStrings.contains("books:write"));
        assertTrue(authorityStrings.contains("books:delete"));
    }

    @Test
    void convert_whenAdminRoleInNamespacedClaim_shouldMapToRoleAdmin() {
        Jwt jwt = createMockJwt(Map.of(
                NAMESPACED_ROLES, List.of("admin")
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        Set<String> authorityStrings = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertEquals(1, authorityStrings.size());
        assertTrue(authorityStrings.contains("ROLE_ADMIN"));
    }

    @Test
    void convert_whenUserRoleInNamespacedClaim_shouldMapToRoleUser() {
        Jwt jwt = createMockJwt(Map.of(
                NAMESPACED_ROLES, List.of("user")
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        Set<String> authorityStrings = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertEquals(1, authorityStrings.size());
        assertTrue(authorityStrings.contains("ROLE_USER"));
    }

    @Test
    void convert_whenBothPermissionsAndRolesPresent_shouldCombineThem() {
        Jwt jwt = createMockJwt(Map.of(
                "permissions", List.of("books:read"),
                NAMESPACED_ROLES, List.of("ROLE_ADMIN")
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        Set<String> authorityStrings = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertEquals(2, authorityStrings.size());
        assertTrue(authorityStrings.contains("books:read"));
        assertTrue(authorityStrings.contains("ROLE_ADMIN"));
    }

    @Test
    void convert_whenFallbackStandardRolesClaimUsed_shouldMapCorrectly() {
        Jwt jwt = createMockJwt(Map.of(
                "roles", List.of("admin", "user")
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        Set<String> authorityStrings = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertEquals(2, authorityStrings.size());
        assertTrue(authorityStrings.contains("ROLE_ADMIN"));
        assertTrue(authorityStrings.contains("ROLE_USER"));
    }

    @Test
    void convert_whenClaimsAreMissing_shouldReturnNoUnexpectedAuthorities() {
        Jwt jwt = createMockJwt(Collections.emptyMap());

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertTrue(authorities.isEmpty());
    }

    @Test
    void convert_whenJwtIsNull_shouldReturnEmpty() {
        Collection<GrantedAuthority> authorities = converter.convert(null);

        assertTrue(authorities.isEmpty());
    }

    private Jwt createMockJwt(Map<String, Object> claims) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaims()).thenReturn(claims);
        return jwt;
    }
}
