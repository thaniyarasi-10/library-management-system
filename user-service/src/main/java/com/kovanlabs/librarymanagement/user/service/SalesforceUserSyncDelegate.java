package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.user.dto.UserResponse;

import java.util.List;

public interface SalesforceUserSyncDelegate {
    void syncUser(Object user);
    List<UserResponse> fetchUsersFromSalesforce();
}
