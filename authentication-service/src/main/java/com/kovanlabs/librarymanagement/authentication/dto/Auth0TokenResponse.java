package com.kovanlabs.librarymanagement.authentication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Auth0TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("id_token") String idToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("scope") String scope
) {}
