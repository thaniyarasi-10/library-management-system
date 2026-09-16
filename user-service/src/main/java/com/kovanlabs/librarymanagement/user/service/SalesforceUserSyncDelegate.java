package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.user.dto.UserResponse;

import java.util.List;

/**
 * Delegate interface for synchronizing user entity changes to and from Salesforce.
 */
public interface SalesforceUserSyncDelegate {

    /**
     * Synchronizes a user entity/DTO with Salesforce Contact records.
     *
     * @param user The user response DTO to sync
     */
    void syncUser(UserResponse user);

    /**
     * Fetches all synced user records from Salesforce.
     *
     * @return List of user response DTOs obtained from Salesforce Contact records
     */
    List<UserResponse> fetchUsersFromSalesforce();
}
