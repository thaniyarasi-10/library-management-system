package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.salesforce.config.SalesforceConfig;
import com.kovanlabs.librarymanagement.salesforce.exception.SalesforceSyncException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class SalesforceClientServiceTest {

    @Mock
    private SalesforceConfig salesforceConfig;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private SalesforceClientService clientService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        clientService = new SalesforceClientService(salesforceConfig, restClient, objectMapper);
    }

    private void mockValidConfig() {
        lenient().when(salesforceConfig.isEnabled()).thenReturn(true);
        lenient().when(salesforceConfig.getClientId()).thenReturn("test-client-id");
        lenient().when(salesforceConfig.getClientSecret()).thenReturn("test-client-secret");
        lenient().when(salesforceConfig.getAuthUrl()).thenReturn("https://login.salesforce.com/services/oauth2/token");
        lenient().when(salesforceConfig.getApiVersion()).thenReturn("v58.0");
    }

    private void mockSuccessfulAuth() {
        String authResponseBody = "{\"access_token\":\"mock-access-token\",\"instance_url\":\"https://mock.salesforce.com\"}";
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andRespond(withSuccess(authResponseBody, MediaType.APPLICATION_JSON));
    }

    // --- isConfigured() tests ---

    @Test
    void isConfigured_whenDisabled_returnsFalse() {
        when(salesforceConfig.isEnabled()).thenReturn(false);
        assertFalse(clientService.isConfigured());
    }

    @Test
    void isConfigured_whenClientIdNullOrBlankOrEmpty_returnsFalse() {
        when(salesforceConfig.isEnabled()).thenReturn(true);
        when(salesforceConfig.getClientId()).thenReturn(null);
        assertFalse(clientService.isConfigured());

        when(salesforceConfig.getClientId()).thenReturn("");
        assertFalse(clientService.isConfigured());

        when(salesforceConfig.getClientId()).thenReturn("   ");
        assertFalse(clientService.isConfigured());
    }

    @Test
    void isConfigured_whenClientSecretNullOrBlankOrEmpty_returnsFalse() {
        when(salesforceConfig.isEnabled()).thenReturn(true);
        when(salesforceConfig.getClientId()).thenReturn("id");
        when(salesforceConfig.getClientSecret()).thenReturn(null);
        assertFalse(clientService.isConfigured());

        when(salesforceConfig.getClientSecret()).thenReturn("");
        assertFalse(clientService.isConfigured());

        when(salesforceConfig.getClientSecret()).thenReturn("   ");
        assertFalse(clientService.isConfigured());
    }

    @Test
    void isConfigured_whenValidCredentials_returnsTrue() {
        mockValidConfig();
        assertTrue(clientService.isConfigured());
    }

    // --- getAccessToken() & getInstanceUrl() & authenticate() tests ---

    @Test
    void getAccessToken_whenNotConfigured_returnsNull() {
        when(salesforceConfig.isEnabled()).thenReturn(false);
        assertNull(clientService.getAccessToken());
        assertNull(clientService.getInstanceUrl());
    }

    @Test
    void getAccessToken_whenConfigured_authenticatesAndReturnsToken() {
        mockValidConfig();
        mockSuccessfulAuth();

        String token = clientService.getAccessToken();
        String instanceUrl = clientService.getInstanceUrl();

        assertEquals("mock-access-token", token);
        assertEquals("https://mock.salesforce.com", instanceUrl);

        // Call again to verify cached values are returned without re-authenticating
        assertEquals("mock-access-token", clientService.getAccessToken());
        assertEquals("https://mock.salesforce.com", clientService.getInstanceUrl());
        mockServer.verify();
    }

    @Test
    void authenticate_whenAuthCallFails_resetsTokenAndUrl() {
        mockValidConfig();
        mockServer
                .expect(ExpectedCount.manyTimes(),
                        requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        String token = clientService.getAccessToken();
        String instanceUrl = clientService.getInstanceUrl();

        assertNull(token);
        assertNull(instanceUrl);
    }

    // --- query() tests ---

    @Test
    void query_whenNotConfigured_returnsNull() {
        when(salesforceConfig.isEnabled()).thenReturn(false);
        assertNull(clientService.query("SELECT Id FROM Account"));
    }

    @Test
    void query_whenSuccessful_returnsJsonNode() {
        mockValidConfig();
        mockSuccessfulAuth();

        String queryResponseBody = "{\"totalSize\":1,\"done\":true,\"records\":[{\"Name\":\"Test Book\"}]}";

        mockServer
                .expect(requestTo(
                        "https://mock.salesforce.com/services/data/v58.0/query?q=SELECT%20Name%20FROM%20Book__c"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-access-token"))
                .andRespond(withSuccess(queryResponseBody, MediaType.APPLICATION_JSON));

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNotNull(result);
        assertEquals(1, result.path("totalSize").asInt());
        assertEquals("Test Book", result.path("records").get(0).path("Name").asText());
        mockServer.verify();
    }

    @Test
    void query_whenServerError_resetsTokenAndReturnsNull() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer
                .expect(requestTo(
                        "https://mock.salesforce.com/services/data/v58.0/query?q=SELECT%20Name%20FROM%20Book__c"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNull(result);
    }

    // --- upsertByExternalId() tests ---

    @Test
    void upsertByExternalId_whenNotConfigured_throwsSalesforceSyncException() {
        when(salesforceConfig.isEnabled()).thenReturn(false);

        assertThrows(SalesforceSyncException.class, () -> clientService.upsertByExternalId("Book__c",
                "External_Book_UUID__c", "uuid-123", Map.of("Name", "Book")));
        mockServer.verify();
    }

    @Test
    void upsertByExternalId_whenSuccessful_executesPatchRequest() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Book__c/External_Book_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-access-token"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withNoContent());

        assertDoesNotThrow(() -> clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123",
                Map.of("Name", "Clean Architecture")));
        mockServer.verify();
    }

    @Test
    void upsertByExternalId_whenServerError_throwsSalesforceSyncException() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Book__c/External_Book_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withServerError());

        assertThrows(SalesforceSyncException.class, () -> clientService.upsertByExternalId("Book__c",
                "External_Book_UUID__c", "uuid-123", Map.of("Name", "Error Book")));
    }

    @Test
    void upsertByExternalId_whenUnauthorized_reauthenticatesAndRetriesSuccessfully() {
        mockValidConfig();
        mockSuccessfulAuth();

        // 1. Initial PATCH returns 401 Unauthorized (expired token)
        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Book__c/External_Book_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-access-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // 2. Re-authentication OAuth call
        String authResponseBody = "{\"access_token\":\"new-mock-access-token\",\"instance_url\":\"https://mock.salesforce.com\"}";
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(authResponseBody, MediaType.APPLICATION_JSON));

        // 3. Retried PATCH with new token succeeds
        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Book__c/External_Book_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer new-mock-access-token"))
                .andRespond(withNoContent());

        assertDoesNotThrow(() -> clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123",
                Map.of("Name", "Clean Architecture")));
        mockServer.verify();
    }

    @Test
    void query_whenUnauthorized_reauthenticatesAndRetriesSuccessfully() {
        mockValidConfig();
        mockSuccessfulAuth();

        // 1. Initial GET returns 401
        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/query?q=SELECT%20Name%20FROM%20Book__c"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-access-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // 2. Re-authentication
        String authResponseBody = "{\"access_token\":\"new-mock-access-token\",\"instance_url\":\"https://mock.salesforce.com\"}";
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(authResponseBody, MediaType.APPLICATION_JSON));

        // 3. Retried GET succeeds
        String queryResponseBody = "{\"totalSize\":1,\"done\":true,\"records\":[{\"Name\":\"Retried Book\"}]}";
        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/query?q=SELECT%20Name%20FROM%20Book__c"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer new-mock-access-token"))
                .andRespond(withSuccess(queryResponseBody, MediaType.APPLICATION_JSON));

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNotNull(result);
        assertEquals("Retried Book", result.path("records").get(0).path("Name").asText());
        mockServer.verify();
    }

    // --- deleteByExternalId() tests ---

    @Test
    void deleteByExternalId_whenNotConfigured_throwsSalesforceSyncException() {
        when(salesforceConfig.isEnabled()).thenReturn(false);

        assertThrows(SalesforceSyncException.class, () -> clientService.deleteByExternalId("Contact",
                "External_User_UUID__c", "uuid-123"));
        mockServer.verify();
    }

    @Test
    void deleteByExternalId_whenSuccessful_executesDeleteRequest() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Contact/External_User_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-access-token"))
                .andRespond(withNoContent());

        assertDoesNotThrow(() -> clientService.deleteByExternalId("Contact", "External_User_UUID__c", "uuid-123"));
        mockServer.verify();
    }

    @Test
    void deleteByExternalId_whenServerError_throwsSalesforceSyncException() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Contact/External_User_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        assertThrows(SalesforceSyncException.class, () -> clientService.deleteByExternalId("Contact",
                "External_User_UUID__c", "uuid-123"));
    }

    @Test
    void deleteByExternalId_whenUnauthorized_reauthenticatesAndRetriesSuccessfully() {
        mockValidConfig();
        mockSuccessfulAuth();

        // 1. Initial DELETE returns 401 Unauthorized
        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Contact/External_User_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-access-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // 2. Re-authentication OAuth call
        String authResponseBody = "{\"access_token\":\"new-mock-access-token\",\"instance_url\":\"https://mock.salesforce.com\"}";
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(authResponseBody, MediaType.APPLICATION_JSON));

        // 3. Retried DELETE with new token succeeds
        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Contact/External_User_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer new-mock-access-token"))
                .andRespond(withNoContent());

        assertDoesNotThrow(() -> clientService.deleteByExternalId("Contact", "External_User_UUID__c", "uuid-123"));
        mockServer.verify();
    }

    @Test
    void deleteByExternalId_whenUnauthorizedAndRetryFails_throwsSalesforceSyncException() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Contact/External_User_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        String authResponseBody = "{\"access_token\":\"new-mock-access-token\",\"instance_url\":\"https://mock.salesforce.com\"}";
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(authResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Contact/External_User_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        assertThrows(SalesforceSyncException.class,
                () -> clientService.deleteByExternalId("Contact", "External_User_UUID__c", "uuid-123"));
    }

    @Test
    void upsertByExternalId_whenUnauthorizedAndRetryFails_throwsSalesforceSyncException() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Book__c/External_Book_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        String authResponseBody = "{\"access_token\":\"new-mock-access-token\",\"instance_url\":\"https://mock.salesforce.com\"}";
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(authResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/sobjects/Book__c/External_Book_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withServerError());

        assertThrows(SalesforceSyncException.class,
                () -> clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Book")));
    }

    @Test
    void query_whenUnauthorizedAndRetryFails_returnsNull() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/query?q=SELECT%20Name%20FROM%20Book__c"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        String authResponseBody = "{\"access_token\":\"new-mock-access-token\",\"instance_url\":\"https://mock.salesforce.com\"}";
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(authResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(
                "https://mock.salesforce.com/services/data/v58.0/query?q=SELECT%20Name%20FROM%20Book__c"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void isUnauthorizedOrExpired_tests() {
        assertFalse(SalesforceClientService.isUnauthorizedOrExpired(null));
        assertTrue(SalesforceClientService.isUnauthorizedOrExpired(new RuntimeException("invalid_session_id expired")));
        assertTrue(SalesforceClientService.isUnauthorizedOrExpired(new RuntimeException(new RuntimeException("session expired"))));
        assertFalse(SalesforceClientService.isUnauthorizedOrExpired(new RuntimeException("random error")));
    }

    @Test
    void isUnauthorizedOrExpired_extendedBranches() {
        HttpStatusCodeException ex401 =
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        assertTrue(SalesforceClientService.isUnauthorizedOrExpired(ex401));

        HttpStatusCodeException ex500 =
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        assertFalse(SalesforceClientService.isUnauthorizedOrExpired(ex500));

        assertTrue(SalesforceClientService.isUnauthorizedOrExpired(new RuntimeException("Error 401 occurred")));
        assertTrue(SalesforceClientService.isUnauthorizedOrExpired(new RuntimeException("Unauthorized request")));
        assertTrue(SalesforceClientService.isUnauthorizedOrExpired(new RuntimeException("invalid_session_id detected")));
        assertTrue(SalesforceClientService.isUnauthorizedOrExpired(new RuntimeException("Your session expired now")));

        assertFalse(SalesforceClientService.isUnauthorizedOrExpired(new RuntimeException((String) null)));
    }

    @Test
    void upsertByExternalId_whenAuthFails_throwsSalesforceSyncExceptionForNullTokenOrHost() {
        mockValidConfig();
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        SalesforceSyncException ex = assertThrows(SalesforceSyncException.class,
                () -> clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of()));
        assertTrue(ex.getMessage().contains("Unable to obtain Salesforce access token or instance URL"));
    }

    @Test
    void deleteByExternalId_whenAuthFails_throwsSalesforceSyncExceptionForNullTokenOrHost() {
        mockValidConfig();
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        SalesforceSyncException ex = assertThrows(SalesforceSyncException.class,
                () -> clientService.deleteByExternalId("Contact", "External_User_UUID__c", "uuid-123"));
        assertTrue(ex.getMessage().contains("Unable to obtain Salesforce access token or instance URL"));
    }

    @Test
    void query_whenAuthFails_returnsNull() {
        mockValidConfig();
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        JsonNode result = clientService.query("SELECT Id FROM Contact");
        assertNull(result);
    }

    @Test
    void reauthenticate_resetsTokensAndAuthenticatesAgain() {
        mockValidConfig();
        mockSuccessfulAuth();

        assertEquals("mock-access-token", clientService.getAccessToken());

        mockServer.reset();
        String authResponseBody = "{\"access_token\":\"refreshed-token\",\"instance_url\":\"https://mock.salesforce.com\"}";
        mockServer.expect(requestTo("https://login.salesforce.com/services/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(authResponseBody, MediaType.APPLICATION_JSON));

        clientService.reauthenticate();
        assertEquals("refreshed-token", clientService.getAccessToken());
        mockServer.verify();
    }
}
