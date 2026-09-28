package com.kovanlabs.librarymanagement.authentication.service;

import com.kovanlabs.librarymanagement.database.enums.RoleEnum;

/**
 * Service interface for synchronizing user roles to Auth0 Management API app_metadata.
 */
public interface Auth0RoleSyncService {

    /**
     * Synchronizes a user's role in MySQL to Auth0 user app_metadata.role.
     *
     * @param auth0Sub The Auth0 user identifier (sub claim)
     * @param role The authoritative user role from MySQL
     */
    void syncUserRole(String auth0Sub, RoleEnum role);
}
