package com.kovanlabs.librarymanagement.authentication.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "auth0.web")
@Getter
@Setter
public class Auth0WebProperties {

    private String clientId;

    private String clientSecret;

    private String redirectUri = "http://localhost:8080/api/auth/callback";

    private String frontendRedirectUri = "http://localhost:5173";

    private String postLogoutRedirectUri = "http://localhost:5173";

    private boolean cookieSecure = false;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
