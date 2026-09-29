package com.kovanlabs.librarymanagement.authentication.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kovanlabs.librarymanagement.authentication.config.Auth0ManagementProperties;
import com.kovanlabs.librarymanagement.authentication.config.Auth0Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Service responsible for requesting and caching Auth0 Management API M2M access tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Auth0TokenService {

    private final Auth0Properties auth0Properties;
    private final Auth0ManagementProperties managementProperties;
    private final RestClient restClient;
    private final Auth0UrlHelper urlHelper;

    private String cachedAccessToken;
    private Instant tokenExpiry = Instant.MIN;

    /**
     * Retrieves or refreshes cached Auth0 Management API access token using client credentials flow.
     */
    public synchronized String getManagementApiToken() {
        if (Objects.nonNull(cachedAccessToken) && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedAccessToken;
        }

        String domain = urlHelper.normalizeDomain(auth0Properties.getDomain());
        String tokenUrl = String.format("https://%s/oauth/token", domain);
        String audience = (Objects.nonNull(managementProperties.getAudience()) && !managementProperties.getAudience().isBlank())
                ? managementProperties.getAudience()
                : String.format("https://%s/api/v2/", domain);

        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "client_id", managementProperties.getClientId(),
                "client_secret", managementProperties.getClientSecret(),
                "audience", audience
        );

        try {
            TokenResponse response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(TokenResponse.class);

            if (Objects.nonNull(response) && Objects.nonNull(response.accessToken())) {
                cachedAccessToken = response.accessToken();
                long expiresIn = Objects.nonNull(response.expiresIn()) ? response.expiresIn() : 3600;
                tokenExpiry = Instant.now().plusSeconds(expiresIn);
                return cachedAccessToken;
            }
        } catch (Exception e) {
            log.error("Error requesting Auth0 Management API token from {}: {}", tokenUrl, e.getMessage());
        }

        return null;
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("scope") String scope
    ) {}
}
