package com.kovanlabs.librarymanagement.authentication.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    public AudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (audience == null || audience.isBlank()) {
            OAuth2Error error = new OAuth2Error("invalid_token", "Configured audience must not be empty", null);
            return OAuth2TokenValidatorResult.failure(error);
        }

        List<String> audiences = jwt.getAudience();
        if (audiences != null && audiences.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }

        OAuth2Error error = new OAuth2Error("invalid_token",
                String.format("The required audience '%s' is missing in token audiences: %s", audience, audiences),
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
