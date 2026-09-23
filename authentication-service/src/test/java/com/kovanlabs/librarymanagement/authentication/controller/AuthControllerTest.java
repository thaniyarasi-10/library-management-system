package com.kovanlabs.librarymanagement.authentication.controller;

import com.kovanlabs.librarymanagement.authentication.config.Auth0AuthoritiesConverter;
import com.kovanlabs.librarymanagement.authentication.config.Auth0WebProperties;
import com.kovanlabs.librarymanagement.authentication.dto.Auth0TokenResponse;
import com.kovanlabs.librarymanagement.authentication.service.Auth0OAuthService;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private Auth0OAuthService oAuthService;

    @Mock
    private Auth0WebProperties webProperties;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private Auth0AuthoritiesConverter authoritiesConverter;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        lenient().when(webProperties.getPostLogoutRedirectUri()).thenReturn("http://localhost:5173");
        lenient().when(webProperties.isCookieSecure()).thenReturn(false);
    }

    @Test
    void login_shouldSetStateCookieAndRedirect() throws Exception {
        when(oAuthService.generateState()).thenReturn("test-state");
        when(oAuthService.buildAuthorizationUrl("test-state"))
                .thenReturn("https://dev-test.auth0.com/authorize?state=test-state");

        authController.login(request, response);

        assertEquals("https://dev-test.auth0.com/authorize?state=test-state", response.getRedirectedUrl());
        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("AUTH0_STATE=test-state"));
        assertTrue(setCookie.contains("HttpOnly"));
    }

    @Test
    void callback_whenErrorParamPresent_shouldRedirectWithAuthError() throws Exception {
        authController.callback(null, "some-state", "access_denied", "User denied", request, response);

        assertEquals("http://localhost:5173?auth_error=access_denied", response.getRedirectedUrl());
    }

    @Test
    void callback_whenStateMismatch_shouldReturn400() throws Exception {
        request.setCookies(new Cookie("AUTH0_STATE", "valid-state"));

        authController.callback("valid-code", "invalid-state", null, null, request, response);

        assertEquals(400, response.getStatus());
    }

    @Test
    void callback_whenMissingCookieState_shouldReturn400() throws Exception {
        authController.callback("valid-code", "valid-state", null, null, request, response);

        assertEquals(400, response.getStatus());
    }

    @Test
    void callback_whenMissingCode_shouldReturn400() throws Exception {
        request.setCookies(new Cookie("AUTH0_STATE", "valid-state"));

        authController.callback("", "valid-state", null, null, request, response);

        assertEquals(400, response.getStatus());
    }

    @Test
    void callback_whenValidCodeAndState_shouldExchangeAndAuthenticate() throws Exception {
        request.setCookies(new Cookie("AUTH0_STATE", "valid-state"));

        Auth0TokenResponse tokenResponse = new Auth0TokenResponse("valid-access-token", "valid-id-token", "Bearer",
                3600L, "openid");
        when(oAuthService.exchangeAuthorizationCode("valid-code")).thenReturn(tokenResponse);

        Jwt jwt = Jwt.withTokenValue("valid-access-token")
                .header("alg", "RS256")
                .claim("sub", "auth0|12345")
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtDecoder.decode("valid-access-token")).thenReturn(jwt);

        authController.callback("valid-code", "valid-state", null, null, request, response);

        verify(userService).syncAuth0User("auth0|12345", "test@example.com", "Test User");
        assertEquals("http://localhost:5173", response.getRedirectedUrl());
        List<String> setCookies = response.getHeaders("Set-Cookie");
        assertNotNull(setCookies);
        assertTrue(setCookies.stream().anyMatch(c -> c.contains("AUTH0_TOKEN=valid-access-token")));
    }

    @Test
    void callback_whenExchangeFails_shouldRedirectWithAuthError() throws Exception {
        request.setCookies(new Cookie("AUTH0_STATE", "valid-state"));
        when(oAuthService.exchangeAuthorizationCode("fail-code")).thenThrow(new RuntimeException("Exchange error"));

        authController.callback("fail-code", "valid-state", null, null, request, response);

        assertEquals("http://localhost:5173?auth_error=auth_failed", response.getRedirectedUrl());
    }

    @Test
    void getToken_whenCookiePresent_shouldReturnAccessToken() {
        request.setCookies(new Cookie("AUTH0_TOKEN", "my-access-token"));

        var res = authController.getToken(request);
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertEquals("my-access-token", body.get("accessToken"));
    }

    @Test
    void getToken_whenCookieMissing_shouldReturn401() {
        var res = authController.getToken(request);
        assertEquals(401, res.getStatusCode().value());
    }

    @Test
    void getMe_whenUnauthenticated_shouldReturn401() {
        var res = authController.getMe(null);
        assertEquals(401, res.getStatusCode().value());

        Authentication anon = mock(AnonymousAuthenticationToken.class);
        var anonRes = authController.getMe(anon);
        assertEquals(401, anonRes.getStatusCode().value());
    }

    @Test
    void getMe_whenAuthenticatedWithJwt_shouldReturnUserResponse() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "auth0|12345")
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")),
                "auth0|12345");

        UserResponse mockUser = new UserResponse(UUID.randomUUID(), 1L, "Test User", "test@example.com", 0);
        when(userService.getUserByIdentifier("auth0|12345")).thenReturn(mockUser);

        var res = authController.getMe(auth);
        assertEquals(200, res.getStatusCode().value());
        assertEquals(mockUser, res.getBody());
    }

    @Test
    void logout_shouldClearCookieAndReturnLogoutUrl() {
        when(oAuthService.buildLogoutUrl())
                .thenReturn("https://dev-test.auth0.com/v2/logout?client_id=test&returnTo=http://localhost:5173");

        var res = authController.logout(request, response);
        assertEquals(200, res.getStatusCode().value());
        assertTrue(res.getBody().containsKey("logoutUrl"));
        assertEquals("https://dev-test.auth0.com/v2/logout?client_id=test&returnTo=http://localhost:5173",
                res.getBody().get("logoutUrl"));

        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("AUTH0_TOKEN="));
        assertTrue(setCookie.contains("Max-Age=0"));
    }
}
