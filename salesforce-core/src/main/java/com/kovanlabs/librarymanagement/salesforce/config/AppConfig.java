package com.kovanlabs.librarymanagement.salesforce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration providing common infrastructure beans for Salesforce integration.
 */
@Configuration
public class AppConfig {

    /**
     * Provides a pre-configured {@link ObjectMapper} supporting Java 8 / Java 21 Date & Time API.
     *
     * @return The configured {@link ObjectMapper} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Provides a shared Spring {@link RestClient} configured with Apache HttpComponents request factory
     * to support standard HTTP methods including PATCH.
     *
     * @return The configured {@link RestClient} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory())
                .build();
    }
}
