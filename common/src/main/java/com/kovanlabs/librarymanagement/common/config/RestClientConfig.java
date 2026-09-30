package com.kovanlabs.librarymanagement.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;


@Configuration
public class RestClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

}
