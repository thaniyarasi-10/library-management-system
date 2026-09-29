package com.kovanlabs.librarymanagement.authentication.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    public AudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (Objects.isNull(audience) || audience.isBlank()) {
            OAuth2Error error = new OAuth2Error("invalid_token", "Configured audience must not be empty", null);
            return OAuth2TokenValidatorResult.failure(error);
        }

        List<String> audiences = jwt.getAudience();
        return (Objects.nonNull(audiences) && audiences.contains(audience))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        String.format("The required audience '%s' is missing in token audiences: %s", audience, audiences),
                        null));
    }
}
