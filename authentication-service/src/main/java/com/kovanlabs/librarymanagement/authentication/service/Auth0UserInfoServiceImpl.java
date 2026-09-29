package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.authentication.dto.Auth0UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Objects;

/**
 * Service implementing {@link Auth0UserInfoService} to retrieve user profile data
 * from the Auth0 /userinfo endpoint via {@link RestClient}.
 */
@Slf4j
@Service
public class Auth0UserInfoServiceImpl implements Auth0UserInfoService {

    private final RestClient restClient;
    private final Auth0UrlHelper auth0UrlHelper;
    private final String defaultDomain;
    private final String explicitUserInfoUrl;

    public Auth0UserInfoServiceImpl(
            @Autowired(required = false) RestClient.Builder restClientBuilder,
            Auth0UrlHelper auth0UrlHelper,
            @Value("${auth0.userinfo-url:}") String explicitUserInfoUrl,
            @Value("${auth0.domain:dev-etrfpmdm1sjiuggl.us.auth0.com}") String auth0Domain) {
        this.restClient = (Objects.nonNull(restClientBuilder) ? restClientBuilder : RestClient.builder()).build();
        this.auth0UrlHelper = Objects.nonNull(auth0UrlHelper) ? auth0UrlHelper : new Auth0UrlHelper();
        this.explicitUserInfoUrl = (Objects.nonNull(explicitUserInfoUrl) && !explicitUserInfoUrl.isBlank())
                ? explicitUserInfoUrl.trim()
                : null;
        this.defaultDomain = this.auth0UrlHelper.normalizeDomain(auth0Domain);
    }

    /**
     * Fetches the user profile from Auth0's /userinfo endpoint using the Bearer access token.
     *
     * @param accessToken The Bearer access token
     * @param issuerUrl   The token issuer URL (optional)
     * @return The {@link Auth0UserProfile} or null if request fails
     */
    @Override
    public Auth0UserProfile fetchUserProfile(String accessToken, String issuerUrl) {
        if (Objects.isNull(accessToken) || accessToken.isBlank()) {
            log.warn("Cannot fetch Auth0 user profile: accessToken is null or blank");
            return null;
        }

        String targetUrl = auth0UrlHelper.resolveUserInfoUrl(explicitUserInfoUrl, issuerUrl, defaultDomain);
        log.debug("Calling Auth0 /userinfo endpoint: {}", targetUrl);

        try {
            return restClient.get()
                    .uri(URI.create(targetUrl))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Auth0UserProfile.class);
        } catch (Exception e) {
            log.error("Failed to fetch user profile from Auth0 userinfo endpoint ({}): {}", targetUrl, e.getMessage());
            return null;
        }
    }
}
