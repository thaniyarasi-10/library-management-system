package com.kovanlabs.librarymanagement.book.dto;

import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import lombok.Builder;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record BorrowResponseDto(
        UUID borrowUuid,
        Long id,
        UUID userId,
        UUID bookId,
        Long bookNumericId,
        String bookTitle,
        String bookAuthor,
        String bookCoverImageUrl,
        Long userNumericId,
        String userName,
        String userEmail,
        LocalDate borrowDate,
        LocalDate dueDate,
        LocalDate returnedDate,
        BorrowStatus status
) implements Serializable {
    public BorrowResponseDto(UUID borrowUuid, Long id, UUID userId, UUID bookId,
                             LocalDate borrowDate, LocalDate dueDate, LocalDate returnedDate, BorrowStatus status) {
        this(borrowUuid, id, userId, bookId, null, null, null, null, null, null, null, borrowDate, dueDate, returnedDate, status);
    }
}

