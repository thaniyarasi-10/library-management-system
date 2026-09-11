package com.kovanlabs.librarymanagement.service;

public interface UserFineChecker {
    boolean hasPendingFines(Long userId);
}
