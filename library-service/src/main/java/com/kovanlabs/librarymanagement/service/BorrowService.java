package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;

import java.util.List;

/**
 * Service interface for book lending, returns, history tracking, and fine verifications.
 */
public interface BorrowService {

    /**
     * Issues a book borrow transaction for an active member without unpaid fines.
     *
     * @param borrowRequestDto The borrow request payload
     * @return The created {@link BorrowResponseDto}
     */
    BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto);

    /**
     * Records the return of a borrowed book and updates its status.
     *
     * @param borrowId The borrow record ID
     * @return The updated {@link BorrowResponseDto}
     */
    BorrowResponseDto returnBook(Long borrowId);

    /**
     * Retrieves all borrow records across the system.
     *
     * @return List of {@link BorrowResponseDto}s
     */
    List<BorrowResponseDto> getAllBorrows();

    /**
     * Retrieves all borrow records associated with a specific user ID.
     *
     * @param userId The user database ID
     * @return List of {@link BorrowResponseDto}s
     */
    List<BorrowResponseDto> getBorrowsByUserId(Long userId);

    /**
     * Retrieves all borrow records associated with a specific user email.
     *
     * @param email The user email address
     * @return List of {@link BorrowResponseDto}s
     */
    List<BorrowResponseDto> getBorrowsByUserEmail(String email);
}
