package com.kovanlabs.librarymanagement.salesforce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
public class SalesforceConfig {

    @Value("${salesforce.enabled:false}")
    private boolean enabled;

    @Value("${salesforce.client-id}")
    private String clientId;

    @Value("${salesforce.client-secret}")
    private String clientSecret;

    @Value("${salesforce.auth-url}")
    private String authUrl;

    @Value("${salesforce.api-version}")
    private String apiVersion;
}
