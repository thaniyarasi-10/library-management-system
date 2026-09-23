package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.authentication.config.Auth0WebProperties;
import com.kovanlabs.librarymanagement.authentication.dto.Auth0TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class Auth0OAuthService {

    private final Auth0WebProperties webProperties;
    private final RestClient restClient;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${auth0.domain:dev-etrfpmdm1sjiuggl.us.auth0.com}")
    private String domain;

    @Value("${auth0.audience:https://library-api.kovanlabs.com}")
    private String audience;

    public String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String buildAuthorizationUrl(String state) {
        String effectiveDomain = normalizeDomain(domain);
        return UriComponentsBuilder.fromUriString("https://" + effectiveDomain + "/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", webProperties.getClientId())
                .queryParam("redirect_uri", webProperties.getRedirectUri())
                .queryParam("scope", "openid profile email")
                .queryParam("audience", audience)
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public Auth0TokenResponse exchangeAuthorizationCode(String code) {
        String effectiveDomain = normalizeDomain(domain);
        String tokenUrl = "https://" + effectiveDomain + "/oauth/token";

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("grant_type", "authorization_code");
        formParams.add("client_id", webProperties.getClientId());
        formParams.add("client_secret", webProperties.getClientSecret());
        formParams.add("code", code);
        formParams.add("redirect_uri", webProperties.getRedirectUri());

        try {
            return restClient.post()
                    .uri(URI.create(tokenUrl))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formParams)
                    .retrieve()
                    .body(Auth0TokenResponse.class);
        } catch (Exception e) {
            log.error("Failed to exchange authorization code with Auth0 at {}: {}", tokenUrl, e.getMessage());
            throw e;
        }
    }

    public String buildLogoutUrl() {
        String effectiveDomain = normalizeDomain(domain);
        String returnTo = webProperties.getPostLogoutRedirectUri();
        return UriComponentsBuilder.fromUriString("https://" + effectiveDomain + "/v2/logout")
                .queryParam("client_id", webProperties.getClientId())
                .queryParam("returnTo", returnTo)
                .build()
                .toUriString();
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return "auth0.local";
        }
        String clean = domain.trim();
        if (clean.startsWith("https://")) {
            clean = clean.substring(8);
        } else if (clean.startsWith("http://")) {
            clean = clean.substring(7);
        }
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
