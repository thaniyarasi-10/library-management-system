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

    private String domain;

    private String audience;

    private String rolesClaim;
}
