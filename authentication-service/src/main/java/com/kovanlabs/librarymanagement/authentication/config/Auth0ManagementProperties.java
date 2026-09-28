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

    private String clientId;

    private String clientSecret;

    private String audience;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
