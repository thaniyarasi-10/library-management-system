package com.kovanlabs.librarymanagement.database.enums;

/**
 * Enumeration representing the status of a user account.
 */
public enum AccountStatus {
    ACTIVE,
    DEACTIVATED;

    /**
     * Checks if the status is deactivated.
     */
    public boolean isDeactivated() {
        return this == DEACTIVATED;
    }
}
