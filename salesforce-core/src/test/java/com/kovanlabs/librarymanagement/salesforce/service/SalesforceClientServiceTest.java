package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.salesforce.config.SalesforceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
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
        mockServer.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(), requestTo("https://login.salesforce.com/services/oauth2/token"))
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

        mockServer.expect(requestTo("https://mock.salesforce.com/services/data/v58.0/query?q=SELECT%20Name%20FROM%20Book__c"))
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

        mockServer.expect(requestTo("https://mock.salesforce.com/services/data/v58.0/query?q=SELECT%20Name%20FROM%20Book__c"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNull(result);
    }

    // --- upsertByExternalId() tests ---

    @Test
    void upsertByExternalId_whenNotConfigured_returnsImmediately() {
        when(salesforceConfig.isEnabled()).thenReturn(false);

        clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Book"));
        mockServer.verify();
    }

    @Test
    void upsertByExternalId_whenSuccessful_executesPatchRequest() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer.expect(requestTo("https://mock.salesforce.com/services/data/v58.0/sobjects/Book__c/External_Book_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-access-token"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withNoContent());

        assertDoesNotThrow(() ->
                clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Clean Architecture"))
        );
        mockServer.verify();
    }

    @Test
    void upsertByExternalId_whenServerError_handlesGracefully() {
        mockValidConfig();
        mockSuccessfulAuth();

        mockServer.expect(requestTo("https://mock.salesforce.com/services/data/v58.0/sobjects/Book__c/External_Book_UUID__c/uuid-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withServerError());

        assertDoesNotThrow(() ->
                clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Error Book"))
        );
    }
}
