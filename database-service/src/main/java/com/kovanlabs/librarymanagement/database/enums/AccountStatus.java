package com.kovanlabs.librarymanagement.database.enums;

public enum AccountStatus {
    ACTIVE,
    DEACTIVATED,
    PERMANENTLY_DEACTIVATED;

    /**
     * Checks if the status is deactivated (either temporary or permanent).
     */
    public boolean isDeactivated() {
        return this == DEACTIVATED || this == PERMANENTLY_DEACTIVATED;
    }

    /**
     * Checks if the status is permanently deactivated.
     */
    public boolean isPermanentlyDeactivated() {
        return this == PERMANENTLY_DEACTIVATED;
    }
}
