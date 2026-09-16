package com.kovanlabs.librarymanagement.salesforce.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class SalesforceConfig {

    @Value("${salesforce.enabled:false}")
    private boolean enabled;

    @Value("${salesforce.client-id:}")
    private String clientId;

    @Value("${salesforce.client-secret:}")
    private String clientSecret;

    @Value("${salesforce.auth-url:https://login.salesforce.com/services/oauth2/token}")
    private String authUrl;

    @Value("${salesforce.api-version:v60.0}")
    private String apiVersion;
}
