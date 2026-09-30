package com.kovanlabs.librarymanagement.authentication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Helper utility for Auth0 URL and domain normalization.
 */
@Component
public class Auth0UrlHelper {

    @Value("${auth0.domain}")
    private String defaultDomain;

    /**
     * Resolves the full /userinfo endpoint URL given optional explicit URL and token issuer URL.
     *
     * @param explicitUserInfoUrl explicitly configured /userinfo URL (optional)
     * @param issuerUrl          issuer URL from JWT (optional)
     * @param defaultDomain      default Auth0 domain
     * @return normalized URL string to Auth0 /userinfo
     */
    public String resolveUserInfoUrl(String explicitUserInfoUrl, String issuerUrl, String defaultDomain) {
        return (Objects.nonNull(explicitUserInfoUrl) && !explicitUserInfoUrl.isBlank())
                ? explicitUserInfoUrl
                : (Objects.nonNull(issuerUrl) && !issuerUrl.isBlank())
                        ? issuerUrl.trim().replaceAll("/+$", "") + "/userinfo"
                        : "https://" + normalizeDomain(defaultDomain) + "/userinfo";
    }

    /**
     * Normalizes a raw domain string by stripping protocols and trailing slashes.
     *
     * @param rawDomain domain string
     * @return clean domain without protocol or trailing slash
     */
    public String normalizeDomain(String rawDomain) {
        if (Objects.isNull(rawDomain)) {
            return defaultDomain;
        }
        String d = rawDomain.trim();
        String withoutProtocol = d.startsWith("https://")
                ? d.substring("https://".length())
                : d.startsWith("http://")
                        ? d.substring("http://".length())
                        : d;
        String clean = withoutProtocol.endsWith("/")
                ? withoutProtocol.substring(0, withoutProtocol.length() - 1)
                : withoutProtocol;
        return clean.isBlank() ? defaultDomain  : clean;
    }
}
