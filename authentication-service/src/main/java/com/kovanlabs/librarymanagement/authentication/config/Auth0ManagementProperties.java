package com.kovanlabs.librarymanagement.authentication.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "auth0.management")
@Getter
@Setter
public class Auth0ManagementProperties {

    /**
     * Auth0 domain (e.g., dev-xxx.us.auth0.com).
     */
    private String domain;

    /**
     * Auth0 Management API Client ID (Machine-to-Machine application).
     */
    private String clientId;

    /**
     * Auth0 Management API Client Secret.
     */
    private String clientSecret;

    /**
     * Auth0 Management API Audience (e.g. https://dev-xxx.us.auth0.com/api/v2/).
     */
    private String audience;

    /**
     * Returns true if client ID and secret are configured.
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
