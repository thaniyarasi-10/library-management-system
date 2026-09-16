package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.user.dto.UserResponse;

import java.util.List;

public interface SalesforceUserSyncDelegate {

    void syncUser(UserResponse user);

    List<UserResponse> fetchUsersFromSalesforce();
}
