package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.authentication.config.Auth0ManagementProperties;
import com.kovanlabs.librarymanagement.authentication.config.Auth0Properties;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class Auth0RoleSyncServiceImplTest {

    private Auth0Properties auth0Properties;
    private Auth0ManagementProperties managementProperties;
    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private Auth0UrlHelper urlHelper;
    private Auth0TokenService tokenService;
    private Auth0RoleSyncServiceImpl auth0RoleSyncServiceImpl;

    @BeforeEach
    void setUp() {
        auth0Properties = new Auth0Properties();
        auth0Properties.setDomain("dev-test.auth0.com");
        auth0Properties.setAudience("https://library-api.kovanlabs.com");
        auth0Properties.setRolesClaim("https://library.kovanlabs.com/roles");

        managementProperties = new Auth0ManagementProperties();
        managementProperties.setClientId("test-client-id");
        managementProperties.setClientSecret("test-client-secret");
        managementProperties.setAudience("https://dev-test.auth0.com/api/v2/");

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        restClient = restClientBuilder.build();

        urlHelper = new Auth0UrlHelper();
        tokenService = new Auth0TokenService(auth0Properties, managementProperties, restClient, urlHelper);

        auth0RoleSyncServiceImpl = new Auth0RoleSyncServiceImpl(
                auth0Properties, managementProperties, tokenService, urlHelper, restClient
        );
    }

    @Test
    void syncUserRole_whenConfigured_shouldFetchTokenAndPatchUserMetadata() {
        String tokenResponseBody = """
                {
                    "access_token": "mock-mgmt-token",
                    "token_type": "Bearer",
                    "expires_in": 86400
                }
                """;

        mockServer.expect(requestTo("https://dev-test.auth0.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(tokenResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://dev-test.auth0.com/api/v2/users/auth0%7C123456"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", "Bearer mock-mgmt-token"))
                .andExpect(content().json("{\"app_metadata\":{\"role\":\"ADMIN\"}}"))
                .andRespond(withSuccess());

        auth0RoleSyncServiceImpl.syncUserRole("auth0|123456", RoleEnum.ADMIN);

        mockServer.verify();
    }

    @Test
    void syncUserRole_whenTokenIsCached_shouldNotFetchTokenAgain() {
        String tokenResponseBody = """
                {
                    "access_token": "mock-cached-token",
                    "token_type": "Bearer",
                    "expires_in": 86400
                }
                """;

        mockServer.expect(requestTo("https://dev-test.auth0.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(tokenResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://dev-test.auth0.com/api/v2/users/auth0%7Cuser1"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", "Bearer mock-cached-token"))
                .andRespond(withSuccess());

        mockServer.expect(requestTo("https://dev-test.auth0.com/api/v2/users/auth0%7Cuser2"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", "Bearer mock-cached-token"))
                .andRespond(withSuccess());

        auth0RoleSyncServiceImpl.syncUserRole("auth0|user1", RoleEnum.USER);
        auth0RoleSyncServiceImpl.syncUserRole("auth0|user2", RoleEnum.USER);

        mockServer.verify();
    }

    @Test
    void syncUserRole_whenNotConfigured_shouldSkipGracefully() {
        managementProperties.setClientId(null);
        managementProperties.setClientSecret(null);

        assertDoesNotThrow(() -> auth0RoleSyncServiceImpl.syncUserRole("auth0|user1", RoleEnum.USER));
    }

    @Test
    void syncUserRole_whenSubOrRoleNull_shouldSkipGracefully() {
        assertDoesNotThrow(() -> auth0RoleSyncServiceImpl.syncUserRole(null, RoleEnum.USER));
        assertDoesNotThrow(() -> auth0RoleSyncServiceImpl.syncUserRole("auth0|user1", null));
    }

    @Test
    void syncUserRole_whenApiErrors_shouldNotThrowException() {
        String tokenResponseBody = """
                {
                    "access_token": "mock-mgmt-token",
                    "token_type": "Bearer",
                    "expires_in": 86400
                }
                """;

        mockServer.expect(requestTo("https://dev-test.auth0.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(tokenResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://dev-test.auth0.com/api/v2/users/auth0%7Cfail"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withServerError());

        assertDoesNotThrow(() -> auth0RoleSyncServiceImpl.syncUserRole("auth0|fail", RoleEnum.USER));
        mockServer.verify();
    }
}
