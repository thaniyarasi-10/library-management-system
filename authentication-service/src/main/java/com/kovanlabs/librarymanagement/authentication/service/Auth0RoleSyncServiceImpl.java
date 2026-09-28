package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.authentication.config.Auth0ManagementProperties;
import com.kovanlabs.librarymanagement.authentication.config.Auth0Properties;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Service for communicating with the Auth0 Management API to synchronize user roles.
 * Implements {@link Auth0RoleSyncService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Auth0RoleSyncServiceImpl implements Auth0RoleSyncService {

    private final Auth0Properties auth0Properties;
    private final Auth0ManagementProperties managementProperties;
    private final Auth0TokenService tokenService;
    private final Auth0UrlHelper urlHelper;
    private final RestClient restClient;

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

        if (!managementProperties.isConfigured()) {
            log.info("Auth0 Management API credentials not configured; skipping app_metadata.role sync for user: {} (role: {})",
                    auth0Sub, role.name());
            return;
        }

        try {
            String token = tokenService.getManagementApiToken();
            if (token == null || token.isBlank()) {
                log.warn("Failed to obtain Auth0 Management API token. Skipping role sync for {}", auth0Sub);
                return;
            }

            String domain = urlHelper.normalizeDomain(auth0Properties.getDomain());
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
}
