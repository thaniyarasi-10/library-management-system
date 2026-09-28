package com.kovanlabs.librarymanagement.authentication.service;

import org.springframework.stereotype.Component;

/**
 * Helper utility for Auth0 URL and domain normalization.
 */
@Component
public class Auth0UrlHelper {

    /**
     * Resolves the full /userinfo endpoint URL given optional explicit URL and token issuer URL.
     *
     * @param explicitUserInfoUrl explicitly configured /userinfo URL (optional)
     * @param issuerUrl          issuer URL from JWT (optional)
     * @param defaultDomain      default Auth0 domain
     * @return normalized URL string to Auth0 /userinfo
     */
    public String resolveUserInfoUrl(String explicitUserInfoUrl, String issuerUrl, String defaultDomain) {
        if (explicitUserInfoUrl != null && !explicitUserInfoUrl.isBlank()) {
            return explicitUserInfoUrl;
        }
        if (issuerUrl != null && !issuerUrl.isBlank()) {
            String cleanIssuer = issuerUrl.trim().replaceAll("/+$", "");
            return cleanIssuer + "/userinfo";
        }
        return "https://" + normalizeDomain(defaultDomain) + "/userinfo";
    }

    /**
     * Normalizes a raw domain string by stripping protocols and trailing slashes.
     *
     * @param rawDomain domain string
     * @return clean domain without protocol or trailing slash
     */
    public String normalizeDomain(String rawDomain) {
        if (rawDomain == null) {
            return "dev-etrfpmdm1sjiuggl.us.auth0.com";
        }
        String d = rawDomain.trim();
        if (d.startsWith("https://")) {
            d = d.substring("https://".length());
        } else if (d.startsWith("http://")) {
            d = d.substring("http://".length());
        }
        if (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d.isBlank() ? "dev-etrfpmdm1sjiuggl.us.auth0.com" : d;
    }
}
