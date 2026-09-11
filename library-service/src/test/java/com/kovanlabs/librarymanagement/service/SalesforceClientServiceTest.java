package com.kovanlabs.librarymanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.config.SalesforceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesforceClientServiceTest {

    @Mock
    private SalesforceConfig salesforceConfig;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private SalesforceClientService clientService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(clientService, "restTemplate", restTemplate);
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
        ResponseEntity<String> authResponse = new ResponseEntity<>(authResponseBody, HttpStatus.OK);
        when(restTemplate.postForEntity(eq("https://login.salesforce.com/services/oauth2/token"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(authResponse);
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
    void getAccessToken_and_getInstanceUrl_whenCached_returnsImmediately() {
        ReflectionTestUtils.setField(clientService, "accessToken", "cached-token");
        ReflectionTestUtils.setField(clientService, "instanceUrl", "https://cached-host.com");

        assertEquals("cached-token", clientService.getAccessToken());
        assertEquals("https://cached-host.com", clientService.getInstanceUrl());

        verifyNoInteractions(restTemplate);
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
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void authenticate_whenAuthCallThrowsException_resetsTokenAndUrl() {
        mockValidConfig();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Auth Service Unavailable"));

        String token = clientService.getAccessToken();
        String instanceUrl = clientService.getInstanceUrl();

        assertNull(token);
        assertNull(instanceUrl);
    }

    @Test
    void authenticate_whenResponseBodyNull_keepsTokenNull() {
        mockValidConfig();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok((String) null));

        assertNull(clientService.getAccessToken());
    }

    @Test
    void authenticate_whenNon2xxStatusReturned_keepsTokenNull() {
        mockValidConfig();
        ResponseEntity<String> badResponse = new ResponseEntity<>("{\"error\":\"invalid_grant\"}", HttpStatus.BAD_REQUEST);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(badResponse);

        assertNull(clientService.getAccessToken());
        assertNull(clientService.getInstanceUrl());
    }

    // --- query() tests ---

    @Test
    void query_whenNotConfigured_returnsNull() {
        when(salesforceConfig.isEnabled()).thenReturn(false);
        assertNull(clientService.query("SELECT Id FROM Account"));
    }

    @Test
    void query_whenTokenOrHostNull_returnsNull() {
        mockValidConfig();

        // 1. Token null, host not null
        ReflectionTestUtils.setField(clientService, "accessToken", null);
        ReflectionTestUtils.setField(clientService, "instanceUrl", "https://mock.salesforce.com");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok((String) null));

        assertNull(clientService.query("SELECT Id FROM Account"));

        // 2. Token not null, host null
        ReflectionTestUtils.setField(clientService, "accessToken", "mock-token");
        ReflectionTestUtils.setField(clientService, "instanceUrl", null);

        assertNull(clientService.query("SELECT Id FROM Account"));
    }

    @Test
    void query_whenSuccessful_returnsJsonNode() {
        mockValidConfig();
        mockSuccessfulAuth();

        String queryResponseBody = "{\"totalSize\":1,\"done\":true,\"records\":[{\"Name\":\"Test Book\"}]}";
        ResponseEntity<String> queryResponse = new ResponseEntity<>(queryResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(queryResponse);

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNotNull(result);
        assertEquals(1, result.path("totalSize").asInt());
        assertEquals("Test Book", result.path("records").get(0).path("Name").asText());
    }

    @Test
    void query_whenExchangeThrowsException_resetsTokenAndReturnsNull() {
        mockValidConfig();
        mockSuccessfulAuth();

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection Timeout"));

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNull(result);
        assertNull(ReflectionTestUtils.getField(clientService, "accessToken"));
    }

    @Test
    void query_whenResponseBodyNull_returnsNull() {
        mockValidConfig();
        mockSuccessfulAuth();

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok((String) null));

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNull(result);
    }

    @Test
    void query_whenNon2xxStatus_returnsNull() {
        mockValidConfig();
        mockSuccessfulAuth();

        ResponseEntity<String> errorResponse = new ResponseEntity<>("{\"error\":\"BAD_REQUEST\"}", HttpStatus.BAD_REQUEST);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(errorResponse);

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNull(result);
    }

    // --- upsertByExternalId() tests ---

    @Test
    void upsertByExternalId_whenNotConfigured_returnsImmediately() {
        when(salesforceConfig.isEnabled()).thenReturn(false);

        clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Book"));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void upsertByExternalId_whenTokenOrHostNull_returnsEarly() {
        mockValidConfig();

        // 1. Token null, host not null
        ReflectionTestUtils.setField(clientService, "accessToken", null);
        ReflectionTestUtils.setField(clientService, "instanceUrl", "https://mock.salesforce.com");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok((String) null));

        clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Book"));
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class));

        // 2. Token not null, host null
        ReflectionTestUtils.setField(clientService, "accessToken", "mock-token");
        ReflectionTestUtils.setField(clientService, "instanceUrl", null);

        clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Book"));
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void upsertByExternalId_whenSuccessful_executesPatchRequest() {
        mockValidConfig();
        mockSuccessfulAuth();

        ResponseEntity<String> patchResponse = ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class)))
                .thenReturn(patchResponse);

        clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Clean Architecture"));

        verify(restTemplate).exchange(
                eq("https://mock.salesforce.com/services/data/v58.0/sobjects/Book__c/External_Book_UUID__c/uuid-123"),
                eq(HttpMethod.PATCH),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void upsertByExternalId_whenCreatedOrOkStatus_logsSuccess() {
        mockValidConfig();
        mockSuccessfulAuth();

        ResponseEntity<String> createdResponse = ResponseEntity.status(HttpStatus.CREATED).body("{\"id\":\"001xxx\"}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class)))
                .thenReturn(createdResponse);

        assertDoesNotThrow(() ->
                clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Created Book"))
        );

        ResponseEntity<String> okResponse = ResponseEntity.ok("{\"id\":\"001xxx\"}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class)))
                .thenReturn(okResponse);

        assertDoesNotThrow(() ->
                clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Updated Book"))
        );
    }

    @Test
    void upsertByExternalId_whenNonSuccessStatusReturned_doesNotLogSuccess() {
        mockValidConfig();
        mockSuccessfulAuth();

        ResponseEntity<String> badResponse = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\":\"invalid_field\"}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class)))
                .thenReturn(badResponse);

        assertDoesNotThrow(() ->
                clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Failed Book"))
        );
    }

    @Test
    void upsertByExternalId_whenExchangeThrowsException_handlesGracefullyAndResetsToken() {
        mockValidConfig();
        mockSuccessfulAuth();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Salesforce 500 Server Error"));

        assertDoesNotThrow(() ->
                clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "uuid-123", Map.of("Name", "Error Book"))
        );
        assertNull(ReflectionTestUtils.getField(clientService, "accessToken"));
    }
}
