package com.kovanlabs.librarymanagement.authentication.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class Auth0UrlHelperTest {

    private Auth0UrlHelper urlHelper;

    @BeforeEach
    void setUp() {
        urlHelper = new Auth0UrlHelper();
        ReflectionTestUtils.setField(urlHelper, "defaultDomain", "default.auth0.com");
    }

    @Test
    @DisplayName("resolveUserInfoUrl should return explicit URL if provided and not blank")
    void resolveUserInfoUrl_withExplicitUrl_shouldReturnExplicitUrl() {
        String url = urlHelper.resolveUserInfoUrl("https://custom.auth0.com/userinfo", "https://issuer.com", "default.com");
        assertEquals("https://custom.auth0.com/userinfo", url);
    }

    @Test
    @DisplayName("resolveUserInfoUrl should return issuer-based URL if explicit is null/blank")
    void resolveUserInfoUrl_withIssuerUrl_shouldReturnIssuerUrl() {
        String url = urlHelper.resolveUserInfoUrl(null, "https://issuer.auth0.com/", "default.com");
        assertEquals("https://issuer.auth0.com/userinfo", url);

        String url2 = urlHelper.resolveUserInfoUrl("  ", "https://issuer.auth0.com", "default.com");
        assertEquals("https://issuer.auth0.com/userinfo", url2);
    }

    @Test
    @DisplayName("resolveUserInfoUrl should fallback to default domain when explicit and issuer are null/blank")
    void resolveUserInfoUrl_withFallback_shouldReturnDefaultDomainUrl() {
        String url = urlHelper.resolveUserInfoUrl(null, null, "my-tenant.auth0.com");
        assertEquals("https://my-tenant.auth0.com/userinfo", url);

        String url2 = urlHelper.resolveUserInfoUrl("", "  ", "my-tenant.auth0.com/");
        assertEquals("https://my-tenant.auth0.com/userinfo", url2);
    }

    @Test
    @DisplayName("normalizeDomain should handle null, blank, http, https, and trailing slashes")
    void normalizeDomain_variations() {
        assertEquals("default.auth0.com", urlHelper.normalizeDomain(null));
        assertEquals("default.auth0.com", urlHelper.normalizeDomain("   "));
        assertEquals("tenant.auth0.com", urlHelper.normalizeDomain("https://tenant.auth0.com/"));
        assertEquals("tenant.auth0.com", urlHelper.normalizeDomain("http://tenant.auth0.com"));
        assertEquals("tenant.auth0.com", urlHelper.normalizeDomain("tenant.auth0.com/"));
        assertEquals("tenant.auth0.com", urlHelper.normalizeDomain("tenant.auth0.com"));
    }
}
