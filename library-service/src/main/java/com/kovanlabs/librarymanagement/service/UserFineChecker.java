package com.kovanlabs.librarymanagement.service;

/**
 * Contract to verify whether a given user has outstanding unpaid fines.
 */
public interface UserFineChecker {

    /**
     * Checks if the user with the specified ID has pending unpaid fines.
     *
     * @param userId The database user ID
     * @return {@code true} if pending fines exist, {@code false} otherwise
     */
    boolean hasPendingFines(Long userId);
}
