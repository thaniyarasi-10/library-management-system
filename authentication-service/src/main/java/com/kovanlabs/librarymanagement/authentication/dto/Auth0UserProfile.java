package com.kovanlabs.librarymanagement.authentication.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable DTO representing user profile data returned by Auth0 /userinfo endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Auth0UserProfile(
        @JsonProperty("sub") String sub,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("nickname") String nickname
) {}
