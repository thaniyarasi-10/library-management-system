package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.salesforce.config.SalesforceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesforceClientServiceTest {

    @Mock
    private SalesforceConfig salesforceConfig;

    @InjectMocks
    private SalesforceClientService clientService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void isConfigured_whenDisabled_returnsFalse() {
        when(salesforceConfig.isEnabled()).thenReturn(false);

        assertFalse(clientService.isConfigured());
    }

    @Test
    void isConfigured_whenMissingCredentials_returnsFalse() {
        when(salesforceConfig.isEnabled()).thenReturn(true);
        when(salesforceConfig.getClientId()).thenReturn("");

        assertFalse(clientService.isConfigured());
    }

    @Test
    void isConfigured_whenValidCredentials_returnsTrue() {
        when(salesforceConfig.isEnabled()).thenReturn(true);
        when(salesforceConfig.getClientId()).thenReturn("client-id");
        when(salesforceConfig.getClientSecret()).thenReturn("client-secret");

        assertTrue(clientService.isConfigured());
    }

    @Test
    void query_whenNotConfigured_returnsNull() {
        when(salesforceConfig.isEnabled()).thenReturn(false);

        JsonNode result = clientService.query("SELECT Name FROM Book__c");

        assertNull(result);
    }

    @Test
    void upsertByExternalId_whenNotConfigured_doesNotThrow() {
        when(salesforceConfig.isEnabled()).thenReturn(false);

        assertDoesNotThrow(() -> clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", "123", Map.of()));
    }
}
