package com.kovanlabs.librarymanagement.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RestClientConfigTest {

    private final RestClientConfig config = new RestClientConfig();

    @Test
    void restClientBuilder_shouldReturnConfiguredBuilder() {
        RestClient.Builder builder = config.restClientBuilder();
        assertNotNull(builder);
    }

    @Test
    void restClient_shouldBuildRestClientSuccessfully() {
        RestClient.Builder builder = config.restClientBuilder();
        RestClient client = config.restClient(builder);
        assertNotNull(client);
    }
}
