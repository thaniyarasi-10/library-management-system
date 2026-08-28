package com.kovanlabs.librarymanagement.book.service;

public interface UserFineChecker {
    boolean hasPendingFines(Long userId);
}
