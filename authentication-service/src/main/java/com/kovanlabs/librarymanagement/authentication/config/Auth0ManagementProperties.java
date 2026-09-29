package com.kovanlabs.librarymanagement.authentication.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Configuration
@ConfigurationProperties(prefix = "auth0.management")
@Getter
@Setter
public class Auth0ManagementProperties {

    private String clientId;

    private String clientSecret;

    private String audience;

    public boolean isConfigured() {
        return Objects.nonNull(clientId) && !clientId.isBlank()
                && Objects.nonNull(clientSecret) && !clientSecret.isBlank();
    }
}
