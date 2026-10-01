package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.authentication.config.Auth0ManagementProperties;
import com.kovanlabs.librarymanagement.authentication.config.Auth0Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class Auth0TokenServiceTest {

    private Auth0Properties auth0Properties;
    private Auth0ManagementProperties managementProperties;
    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private Auth0UrlHelper urlHelper;
    private Auth0TokenService tokenService;

    @BeforeEach
    void setUp() {
        auth0Properties = new Auth0Properties();
        auth0Properties.setDomain("dev-test.auth0.com");

        managementProperties = new Auth0ManagementProperties();
        managementProperties.setClientId("test-client-id");
        managementProperties.setClientSecret("test-client-secret");
        managementProperties.setAudience("https://dev-test.auth0.com/api/v2/");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        urlHelper = new Auth0UrlHelper();

        tokenService = new Auth0TokenService(auth0Properties, managementProperties, restClient, urlHelper);
    }

    @Test
    @DisplayName("getManagementApiToken should request new token and return accessToken")
    void getManagementApiToken_shouldReturnToken() {
        String tokenResponseBody = """
                {
                    "access_token": "token-12345",
                    "token_type": "Bearer",
                    "expires_in": 3600,
                    "scope": "read:users update:users"
                }
                """;

        mockServer.expect(requestTo("https://dev-test.auth0.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(tokenResponseBody, MediaType.APPLICATION_JSON));

        String token = tokenService.getManagementApiToken();

        assertEquals("token-12345", token);
        mockServer.verify();
    }

    @Test
    @DisplayName("getManagementApiToken should return cached token when not expired")
    void getManagementApiToken_whenCached_shouldReturnCachedTokenWithoutRequest() {
        String tokenResponseBody = """
                {
                    "access_token": "cached-token-123",
                    "token_type": "Bearer",
                    "expires_in": 7200
                }
                """;

        mockServer.expect(requestTo("https://dev-test.auth0.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(tokenResponseBody, MediaType.APPLICATION_JSON));

        String firstToken = tokenService.getManagementApiToken();
        String secondToken = tokenService.getManagementApiToken();

        assertEquals("cached-token-123", firstToken);
        assertEquals("cached-token-123", secondToken);
        mockServer.verify();
    }

    @Test
    @DisplayName("getManagementApiToken should derive default audience if audience is blank")
    void getManagementApiToken_whenAudienceBlank_shouldUseDefaultAudience() {
        managementProperties.setAudience("");

        String tokenResponseBody = """
                {
                    "access_token": "token-default-aud",
                    "token_type": "Bearer"
                }
                """;

        mockServer.expect(requestTo("https://dev-test.auth0.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                            "grant_type": "client_credentials",
                            "client_id": "test-client-id",
                            "client_secret": "test-client-secret",
                            "audience": "https://dev-test.auth0.com/api/v2/"
                        }
                        """))
                .andRespond(withSuccess(tokenResponseBody, MediaType.APPLICATION_JSON));

        String token = tokenService.getManagementApiToken();
        assertEquals("token-default-aud", token);
        mockServer.verify();
    }

    @Test
    @DisplayName("getManagementApiToken should return null when request fails with exception")
    void getManagementApiToken_whenError_shouldReturnNull() {
        mockServer.expect(requestTo("https://dev-test.auth0.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        String token = tokenService.getManagementApiToken();
        assertNull(token);
        mockServer.verify();
    }
}
