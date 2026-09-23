package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.authentication.config.Auth0WebProperties;
import com.kovanlabs.librarymanagement.authentication.dto.Auth0TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class Auth0OAuthServiceTest {

    private Auth0WebProperties webProperties;
    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private Auth0OAuthService oAuthService;

    @BeforeEach
    void setUp() {
        webProperties = new Auth0WebProperties();
        webProperties.setClientId("test-client-id");
        webProperties.setClientSecret("test-client-secret");
        webProperties.setRedirectUri("http://localhost:8080/api/auth/callback");
        webProperties.setPostLogoutRedirectUri("http://localhost:5173");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();

        oAuthService = new Auth0OAuthService(webProperties, restClient);
        ReflectionTestUtils.setField(oAuthService, "domain", "dev-test.auth0.com");
        ReflectionTestUtils.setField(oAuthService, "audience", "https://library-api.kovanlabs.com");
    }

    @Test
    void generateState_shouldReturnNonEmptyBase64String() {
        String state = oAuthService.generateState();
        assertNotNull(state);
        assertFalse(state.isBlank());
        assertTrue(state.length() >= 32);
    }

    @Test
    void buildAuthorizationUrl_shouldContainAllRequiredParams() {
        String state = "test-state-123";
        String url = oAuthService.buildAuthorizationUrl(state);

        assertNotNull(url);
        assertTrue(url.startsWith("https://dev-test.auth0.com/authorize"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("redirect_uri=http://localhost:8080/api/auth/callback"));
        assertTrue(url.contains("audience=https://library-api.kovanlabs.com"));
        assertTrue(url.contains("state=test-state-123"));
        assertTrue(url.contains("scope=openid%20profile%20email") || url.contains("scope=openid+profile+email") || url.contains("scope=openid profile email"));
    }

    @Test
    void exchangeAuthorizationCode_whenSuccessful_shouldReturnTokens() {
        String tokenResponseBody = """
                {
                    "access_token": "mock-access-token",
                    "id_token": "mock-id-token",
                    "token_type": "Bearer",
                    "expires_in": 86400,
                    "scope": "openid profile email"
                }
                """;

        mockServer.expect(requestTo("https://dev-test.auth0.com/oauth/token"))
                .andExpect(method(POST))
                .andExpect(header("Content-Type", APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=test-client-id")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=test-client-secret")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=test-code")))
                .andRespond(withSuccess(tokenResponseBody, APPLICATION_JSON));

        Auth0TokenResponse response = oAuthService.exchangeAuthorizationCode("test-code");

        assertNotNull(response);
        assertEquals("mock-access-token", response.accessToken());
        assertEquals("mock-id-token", response.idToken());
        assertEquals(86400L, response.expiresIn());
        mockServer.verify();
    }

    @Test
    void exchangeAuthorizationCode_whenAuth0Fails_shouldThrowException() {
        mockServer.expect(requestTo("https://dev-test.auth0.com/oauth/token"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThrows(Exception.class, () -> oAuthService.exchangeAuthorizationCode("bad-code"));
        mockServer.verify();
    }

    @Test
    void buildLogoutUrl_shouldContainClientIdAndReturnTo() {
        String logoutUrl = oAuthService.buildLogoutUrl();
        assertNotNull(logoutUrl);
        assertTrue(logoutUrl.startsWith("https://dev-test.auth0.com/v2/logout"));
        assertTrue(logoutUrl.contains("client_id=test-client-id"));
        assertTrue(logoutUrl.contains("returnTo=http://localhost:5173"));
    }
}
