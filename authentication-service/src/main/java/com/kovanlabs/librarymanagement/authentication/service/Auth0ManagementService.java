package com.kovanlabs.librarymanagement.authentication.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kovanlabs.librarymanagement.authentication.config.Auth0ManagementProperties;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.user.service.Auth0RoleSyncDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.net.URI;

/**
 * Service for communicating with the Auth0 Management API to synchronize user roles.
 * Implements {@link Auth0RoleSyncDelegate}.
 */
@Slf4j
@Service
public class Auth0ManagementService implements Auth0RoleSyncDelegate {

    private final Auth0ManagementProperties properties;
    private final RestClient restClient;
    private final String fallbackDomain;

    private String cachedAccessToken;
    private Instant tokenExpiry = Instant.MIN;

    public Auth0ManagementService(
            Auth0ManagementProperties properties,
            RestClient.Builder restClientBuilder,
            @Value("${auth0.domain:dev-default.us.auth0.com}") String fallbackDomain) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.fallbackDomain = fallbackDomain;
    }

    /**
     * Synchronizes a user's role from MySQL to Auth0 app_metadata.role via Management API PATCH /api/v2/users/{sub}.
     *
     * @param auth0Sub The Auth0 user identifier (sub claim)
     * @param role The authoritative user role from MySQL
     */
    @Override
    public void syncUserRole(String auth0Sub, RoleEnum role) {
        if (auth0Sub == null || auth0Sub.isBlank()) {
            log.warn("Cannot sync role to Auth0: auth0Sub is null or blank");
            return;
        }
        if (role == null) {
            log.warn("Cannot sync role to Auth0 for user {}: role is null", auth0Sub);
            return;
        }

        if (!properties.isConfigured()) {
            log.info("Auth0 Management API credentials not configured; skipping app_metadata.role sync for user: {} (role: {})",
                    auth0Sub, role.name());
            return;
        }

        try {
            String token = getManagementApiToken();
            if (token == null || token.isBlank()) {
                log.warn("Failed to obtain Auth0 Management API token. Skipping role sync for {}", auth0Sub);
                return;
            }

            String domain = getEffectiveDomain();
            String encodedSub = URLEncoder.encode(auth0Sub, StandardCharsets.UTF_8);
            String url = String.format("https://%s/api/v2/users/%s", domain, encodedSub);

            Map<String, Object> body = Map.of(
                    "app_metadata", Map.of("role", role.name())
            );

            restClient.patch()
                    .uri(URI.create(url))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Successfully synced role {} to Auth0 app_metadata for user {}", role.name(), auth0Sub);
        } catch (Exception e) {
            log.error("Failed to sync role {} to Auth0 app_metadata for user {}: {}", role.name(), auth0Sub, e.getMessage());
        }
    }

    /**
     * Retrieves or refreshes cached Auth0 Management API access token using client credentials flow.
     */
    public synchronized String getManagementApiToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedAccessToken;
        }

        String domain = getEffectiveDomain();
        String tokenUrl = String.format("https://%s/oauth/token", domain);
        String audience = (properties.getAudience() != null && !properties.getAudience().isBlank())
                ? properties.getAudience()
                : String.format("https://%s/api/v2/", domain);

        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "client_id", properties.getClientId(),
                "client_secret", properties.getClientSecret(),
                "audience", audience
        );

        try {
            TokenResponse response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response != null && response.accessToken() != null) {
                cachedAccessToken = response.accessToken();
                long expiresIn = response.expiresIn() != null ? response.expiresIn() : 3600;
                tokenExpiry = Instant.now().plusSeconds(expiresIn);
                return cachedAccessToken;
            }
        } catch (Exception e) {
            log.error("Error requesting Auth0 Management API token from {}: {}", tokenUrl, e.getMessage());
        }

        return null;
    }

    private String getEffectiveDomain() {
        if (properties.getDomain() != null && !properties.getDomain().isBlank()) {
            return normalizeDomain(properties.getDomain());
        }
        return normalizeDomain(fallbackDomain);
    }

    private String normalizeDomain(String rawDomain) {
        if (rawDomain == null) return "";
        String d = rawDomain.trim();
        if (d.startsWith("https://")) {
            d = d.substring("https://".length());
        } else if (d.startsWith("http://")) {
            d = d.substring("http://".length());
        }
        if (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("scope") String scope
    ) {}
}
