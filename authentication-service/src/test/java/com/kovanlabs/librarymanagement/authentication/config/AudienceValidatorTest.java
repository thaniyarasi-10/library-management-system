package com.kovanlabs.librarymanagement.authentication.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudienceValidatorTest {

    private static final String AUDIENCE = "https://library-api.kovanlabs.com";
    private AudienceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AudienceValidator(AUDIENCE);
    }

    @Test
    void validate_whenCorrectAudience_shouldBeValid() {
        Jwt jwt = createMockJwt(List.of(AUDIENCE));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertFalse(result.hasErrors());
    }

    @Test
    void validate_whenMissingAudience_shouldBeInvalid() {
        Jwt jwt = createMockJwt(Collections.emptyList());

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
    }

    @Test
    void validate_whenIncorrectAudience_shouldBeInvalid() {
        Jwt jwt = createMockJwt(List.of("https://wrong-api.kovanlabs.com"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
    }

    @Test
    void validate_whenMultipleAudiencesIncludeTarget_shouldBeValid() {
        Jwt jwt = createMockJwt(List.of("https://other.api", AUDIENCE));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertFalse(result.hasErrors());
    }

    @Test
    void validate_whenAudienceIsNullInJwt_shouldBeInvalid() {
        Jwt jwt = createMockJwt(null);

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
    }

    @Test
    void validate_whenConfiguredAudienceIsEmpty_shouldBeInvalid() {
        AudienceValidator emptyAudienceValidator = new AudienceValidator("");
        Jwt jwt = createMockJwt(List.of(AUDIENCE));

        OAuth2TokenValidatorResult result = emptyAudienceValidator.validate(jwt);

        assertTrue(result.hasErrors());
    }

    private Jwt createMockJwt(List<String> audiences) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(audiences);
        return jwt;
    }
}
