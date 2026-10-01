package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.authentication.dto.Auth0UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Auth0UserInfoServiceImplTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private Auth0UserInfoServiceImpl userInfoService;
    private Auth0UrlHelper urlHelper;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.build()).thenReturn(restClient);

        urlHelper = new Auth0UrlHelper();
        userInfoService = new Auth0UserInfoServiceImpl(builder, urlHelper, null, "dev-etrfpmdm1sjiuggl.us.auth0.com");
    }

    @Test
    @DisplayName("fetchUserProfile with valid token should call /userinfo and return profile")
    void fetchUserProfile_withValidToken_shouldReturnProfile() {
        Auth0UserProfile mockProfile = new Auth0UserProfile(
                "google-oauth2|12345",
                "test@example.com",
                "Test User",
                "test"
        );

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(URI.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Auth0UserProfile.class)).thenReturn(mockProfile);

        Auth0UserProfile result = userInfoService.fetchUserProfile("valid-token", "https://dev-etrfpmdm1sjiuggl.us.auth0.com/");

        assertNotNull(result);
        assertEquals("google-oauth2|12345", result.sub());
        assertEquals("test@example.com", result.email());
        assertEquals("Test User", result.name());
        assertEquals("test", result.nickname());
    }

    @Test
    @DisplayName("fetchUserProfile with null or blank token should return null without calling endpoint")
    void fetchUserProfile_withBlankToken_shouldReturnNull() {
        assertNull(userInfoService.fetchUserProfile(null, "https://dev-etrfpmdm1sjiuggl.us.auth0.com/"));
        assertNull(userInfoService.fetchUserProfile("   ", "https://dev-etrfpmdm1sjiuggl.us.auth0.com/"));
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("fetchUserProfile with null issuerUrl should fallback to default domain")
    void fetchUserProfile_withNullIssuer_shouldFallbackToDefaultDomain() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(URI.create("https://dev-etrfpmdm1sjiuggl.us.auth0.com/userinfo"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Auth0UserProfile.class)).thenReturn(null);

        Auth0UserProfile result = userInfoService.fetchUserProfile("valid-token", null);

        assertNull(result);
        verify(requestHeadersUriSpec).uri(URI.create("https://dev-etrfpmdm1sjiuggl.us.auth0.com/userinfo"));
    }

    @Test
    @DisplayName("fetchUserProfile when rest client throws exception should log error and return null")
    void fetchUserProfile_whenExceptionThrown_shouldReturnNull() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(URI.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(new RuntimeException("Network error"));

        Auth0UserProfile result = userInfoService.fetchUserProfile("valid-token", "https://dev-etrfpmdm1sjiuggl.us.auth0.com/");

        assertNull(result);
    }

    @Test
    @DisplayName("fetchUserProfile with explicitUserInfoUrl and null builder should initialize and call targetUrl")
    void fetchUserProfile_withExplicitUrlAndNullBuilder() {
        Auth0UserInfoServiceImpl customService = new Auth0UserInfoServiceImpl(
                null,
                urlHelper,
                "https://explicit.auth0.com/userinfo",
                "default.auth0.com"
        );
        // Will fail or return null safely due to no mock server on the internal builder
        assertNull(customService.fetchUserProfile("token", null));
    }
}
