package com.kovanlabs.librarymanagement.authentication.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "auth0")
@Getter
@Setter
public class Auth0Properties {

    private String domain = "dev-default.us.auth0.com";

    private String audience = "https://library-api.kovanlabs.com";

    private String rolesClaim = "https://library.kovanlabs.com/roles";
}
