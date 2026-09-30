package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.authentication.dto.Auth0UserProfile;

/**
 * Service interface for retrieving user profile data from Auth0 /userinfo endpoint.
 */
public interface Auth0UserInfoService {

    /**
     * Fetches the user's profile from the Auth0 /userinfo endpoint using the provided access token.
     *
     * @param accessToken The Bearer access token string
     * @param issuerUrl   The token issuer URL (optional, can be null)
     * @return The fetched {@link Auth0UserProfile} or null if fetch fails
     */
    Auth0UserProfile fetchUserProfile(String accessToken, String issuerUrl);
}
